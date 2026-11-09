package com.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.entity.MallPriceRemind;
import com.mall.mapper.MallPriceRemindMapper;
import com.mall.service.MallNoticeService;
import com.mall.service.MallPriceRemindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 降价提醒实现（V13）。
 *
 * <p>幂等三件套：{@code uk_price_remind_member_goods} 唯一键防重复订阅、
 * {@code WHERE status=0} 条件更新防重复通知、{@code notify_time} 记录触发时间便于排查。
 */
@Slf4j
@Service
public class MallPriceRemindServiceImpl extends ServiceImpl<MallPriceRemindMapper, MallPriceRemind>
        implements MallPriceRemindService {

    /** 单轮扫描上限：宁可多扫几轮，也不让一次定时任务把库拖住 */
    private static final int SWEEP_BATCH = 200;

    private final GoodsInnerClient goodsInnerClient;
    private final MallNoticeService noticeService;

    public MallPriceRemindServiceImpl(GoodsInnerClient goodsInnerClient, MallNoticeService noticeService) {
        this.goodsInnerClient = goodsInnerClient;
        this.noticeService = noticeService;
    }

    @Override
    public void subscribe(Long memberId, Long goodsId, Long skuId, Long expectedPrice) {
        if (memberId == null || goodsId == null) {
            throw new BusinessException(400, "参数错误：goodsId 必填");
        }
        long price = expectedPrice == null ? 0L : expectedPrice;
        if (price < 0) {
            throw new BusinessException(400, "期望价不能为负");
        }
        long sku = skuId == null ? 0L : skuId;
        Long current = currentPrice(sku, goodsId);
        MallPriceRemind exist = getOne(new LambdaQueryWrapper<MallPriceRemind>()
                .eq(MallPriceRemind::getMemberId, memberId)
                .eq(MallPriceRemind::getGoodsId, goodsId)
                .eq(MallPriceRemind::getSkuId, sku)
                .last("LIMIT 1"), false);
        if (exist != null) {
            // 已订阅过：只更新期望价，状态已经是「已通知」的保持不动（避免反复骚扰）
            MallPriceRemind patch = new MallPriceRemind();
            patch.setId(exist.getId());
            patch.setExpectedPrice(price);
            patch.setCurrentPrice(current);
            updateById(patch);
            return;
        }
        MallPriceRemind remind = new MallPriceRemind();
        remind.setMemberId(memberId);
        remind.setGoodsId(goodsId);
        remind.setSkuId(sku);
        remind.setExpectedPrice(price);
        remind.setCurrentPrice(current);
        remind.setStatus(MallPriceRemind.STATUS_WATCHING);
        try {
            save(remind);
        } catch (DuplicateKeyException e) {
            // 并发重复订阅：唯一键兜底，按「已订阅」处理
            log.debug("降价提醒重复订阅：memberId={}, goodsId={}", memberId, goodsId);
        }
    }

    @Override
    public List<Map<String, Object>> myReminds(Long memberId) {
        List<MallPriceRemind> list = list(new LambdaQueryWrapper<MallPriceRemind>()
                .eq(MallPriceRemind::getMemberId, memberId)
                .orderByDesc(MallPriceRemind::getId));
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> skuIds = list.stream().map(MallPriceRemind::getSkuId)
                .filter(id -> id != null && id > 0).distinct().toList();
        Map<Long, SkuBrief> skuMap = new LinkedHashMap<>();
        if (!skuIds.isEmpty()) {
            try {
                skuMap = goodsInnerClient.skuByIds(skuIds).stream()
                        .collect(Collectors.toMap(SkuBrief::getId, Function.identity()));
            } catch (Exception e) {
                log.debug("读取 SKU 现价失败（按订阅时价格展示）：{}", e.getMessage());
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MallPriceRemind remind : list) {
            SkuBrief sku = skuMap.get(remind.getSkuId());
            long now = sku != null && sku.getPrice() != null ? sku.getPrice()
                    : (remind.getCurrentPrice() == null ? 0L : remind.getCurrentPrice());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(remind.getId()));
            row.put("goodsId", String.valueOf(remind.getGoodsId()));
            row.put("skuId", String.valueOf(remind.getSkuId()));
            row.put("expectedPrice", remind.getExpectedPrice());
            row.put("currentPrice", now);
            row.put("reached", now <= (remind.getExpectedPrice() == null ? 0L : remind.getExpectedPrice()));
            row.put("status", remind.getStatus());
            row.put("statusText", switch (remind.getStatus() == null ? 0 : remind.getStatus()) {
                case MallPriceRemind.STATUS_NOTIFIED -> "已通知";
                case MallPriceRemind.STATUS_INVALID -> "已失效";
                default -> "监控中";
            });
            row.put("notifyTime", remind.getNotifyTime());
            row.put("createTime", remind.getCreateTime());
            rows.add(row);
        }
        return rows;
    }

    @Override
    public void cancel(Long memberId, Long id) {
        MallPriceRemind remind = getById(id);
        if (remind == null || !remind.getMemberId().equals(memberId)) {
            throw new BusinessException(400, "订阅不存在");
        }
        removeById(id);
    }

    @Override
    public int sweep() {
        List<MallPriceRemind> watching = list(new LambdaQueryWrapper<MallPriceRemind>()
                .eq(MallPriceRemind::getStatus, MallPriceRemind.STATUS_WATCHING)
                .orderByAsc(MallPriceRemind::getId)
                .last("LIMIT " + SWEEP_BATCH));
        if (watching.isEmpty()) {
            return 0;
        }
        List<Long> skuIds = watching.stream().map(MallPriceRemind::getSkuId)
                .filter(id -> id != null && id > 0).distinct().toList();
        Map<Long, Long> priceMap = new LinkedHashMap<>();
        if (!skuIds.isEmpty()) {
            try {
                for (SkuBrief sku : goodsInnerClient.skuByIds(skuIds)) {
                    if (sku != null && sku.getPrice() != null) {
                        priceMap.put(sku.getId(), sku.getPrice());
                    }
                }
            } catch (Exception e) {
                log.warn("降价提醒扫描跳过本轮：商品服务不可用（{}）", e.getMessage());
                return 0;
            }
        }
        int fired = 0;
        for (MallPriceRemind remind : watching) {
            Long current = priceMap.get(remind.getSkuId());
            if (current == null || current > remind.getExpectedPrice()) {
                continue;
            }
            // 条件更新：只有还在「监控中」才能改成「已通知」，并发/重复扫描都不会通知两次
            int rows = baseMapper.update(null, new LambdaUpdateWrapper<MallPriceRemind>()
                    .eq(MallPriceRemind::getId, remind.getId())
                    .eq(MallPriceRemind::getStatus, MallPriceRemind.STATUS_WATCHING)
                    .set(MallPriceRemind::getStatus, MallPriceRemind.STATUS_NOTIFIED)
                    .set(MallPriceRemind::getCurrentPrice, current)
                    .set(MallPriceRemind::getNotifyTime, LocalDateTime.now()));
            if (rows == 0) {
                continue;
            }
            fired++;
            try {
                noticeService.push(remind.getMemberId(), 2, "降价提醒",
                        "您关注的商品已降到 " + yuan(current) + " 元（期望 " + yuan(remind.getExpectedPrice()) + " 元）",
                        String.valueOf(remind.getGoodsId()));
            } catch (Exception e) {
                log.warn("降价提醒站内信推送失败：memberId={}, {}", remind.getMemberId(), e.getMessage());
            }
        }
        if (fired > 0) {
            log.info("降价提醒本轮通知 {} 条", fired);
        }
        return fired;
    }

    private Long currentPrice(Long skuId, Long goodsId) {
        if (skuId == null || skuId <= 0) {
            return null;
        }
        try {
            List<SkuBrief> skus = goodsInnerClient.skuByIds(List.of(skuId));
            if (!skus.isEmpty() && skus.get(0) != null) {
                return skus.get(0).getPrice();
            }
        } catch (Exception e) {
            log.debug("订阅时读取 SKU 现价失败：skuId={}, {}", skuId, e.getMessage());
        }
        return null;
    }

    private static String yuan(long fen) {
        return (fen % 100 == 0) ? String.valueOf(fen / 100) : String.format("%.2f", fen / 100.0);
    }
}
