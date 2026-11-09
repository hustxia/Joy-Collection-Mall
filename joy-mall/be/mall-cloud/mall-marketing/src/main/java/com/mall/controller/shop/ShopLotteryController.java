package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.MemberInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.PointGrantDTO;
import com.mall.common.feign.dto.PromoOrderCreateDTO;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.common.result.Result;
import com.mall.entity.MallCoupon;
import com.mall.entity.MallCouponRecord;
import com.mall.entity.MallLotteryActivity;
import com.mall.entity.MallLotteryPrize;
import com.mall.entity.MallLotteryRecord;
import com.mall.service.MallCouponRecordService;
import com.mall.service.MallCouponService;
import com.mall.service.MallLotteryActivityService;
import com.mall.service.MallLotteryPrizeService;
import com.mall.service.MallLotteryRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C 端抽奖（V8）：活动列表 / 抽奖 / 我的记录 / 今日剩余次数。
 *
 * <p>抽奖主流程（同事务）：时间窗校验 → 日限计数 → 权重随机选品 →（实物先校验地址、券先校验可用性）
 * → 参与消耗积分（远程，参与成本不退）→ 限量奖品条件更新扣减（兜底防超发，扣不动则降级谢谢参与）
 * → 权益发放（积分走 member /inner/point/grant、优惠券本域落 record、实物建 order_type=3 零元单）
 * → 落抽奖记录。远程调用不随本地事务回滚：积分消耗语义即「参与成本」，与是否中奖无关。
 */
@Tag(name = "商城前台-抽奖", description = "抽奖活动/抽奖/中奖记录")
@RestController
@RequestMapping("/shop/lottery")
public class ShopLotteryController {

    private final MallLotteryActivityService lotteryActivityService;
    private final MallLotteryPrizeService lotteryPrizeService;
    private final MallLotteryRecordService lotteryRecordService;
    private final MallCouponService mallCouponService;
    private final MallCouponRecordService mallCouponRecordService;
    private final MemberInnerClient memberInnerClient;
    private final GoodsInnerClient goodsInnerClient;
    private final OrderInnerClient orderInnerClient;
    /** 只用于本域 mall_lottery_prize 的库存条件更新 / mall_coupon 的防超发，不跨域 */
    private final JdbcTemplate jdbcTemplate;

    public ShopLotteryController(MallLotteryActivityService lotteryActivityService,
                                 MallLotteryPrizeService lotteryPrizeService,
                                 MallLotteryRecordService lotteryRecordService,
                                 MallCouponService mallCouponService,
                                 MallCouponRecordService mallCouponRecordService,
                                 MemberInnerClient memberInnerClient,
                                 GoodsInnerClient goodsInnerClient,
                                 OrderInnerClient orderInnerClient,
                                 JdbcTemplate jdbcTemplate) {
        this.lotteryActivityService = lotteryActivityService;
        this.lotteryPrizeService = lotteryPrizeService;
        this.lotteryRecordService = lotteryRecordService;
        this.mallCouponService = mallCouponService;
        this.mallCouponRecordService = mallCouponRecordService;
        this.memberInnerClient = memberInnerClient;
        this.goodsInnerClient = goodsInnerClient;
        this.orderInnerClient = orderInnerClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "抽奖活动列表：进行中（status=1 且在时间窗内），含奖品池")
    @GetMapping("/activities")
    public Result<List<Map<String, Object>>> activities() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<MallLotteryActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallLotteryActivity::getStatus, 1)
                .le(MallLotteryActivity::getStartTime, now)
                .ge(MallLotteryActivity::getEndTime, now)
                .orderByDesc(MallLotteryActivity::getId);
        List<MallLotteryActivity> activities = lotteryActivityService.list(wrapper);

        List<Long> activityIds = activities.stream().map(MallLotteryActivity::getId).toList();
        Map<Long, List<MallLotteryPrize>> prizeMap = activityIds.isEmpty() ? Map.of()
                : lotteryPrizeService.list(new LambdaQueryWrapper<MallLotteryPrize>()
                        .in(MallLotteryPrize::getActivityId, activityIds)
                        .orderByAsc(MallLotteryPrize::getSort).orderByAsc(MallLotteryPrize::getId))
                .stream().collect(Collectors.groupingBy(MallLotteryPrize::getActivityId));

        List<Map<String, Object>> rows = activities.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", String.valueOf(a.getId()));
            m.put("name", a.getName());
            m.put("dailyLimit", a.getDailyLimit());
            m.put("consumePoints", a.getConsumePoints());
            m.put("startTime", a.getStartTime());
            m.put("endTime", a.getEndTime());
            m.put("prizes", prizeMap.getOrDefault(a.getId(), List.of()).stream().map(p -> {
                Map<String, Object> pm = new LinkedHashMap<String, Object>();
                pm.put("id", String.valueOf(p.getId()));
                pm.put("prizeType", p.getPrizeType());
                pm.put("name", p.getName());
                pm.put("stock", p.getStock());
                pm.put("sort", p.getSort());
                return pm;
            }).toList());
            return m;
        }).toList();
        return Result.ok(rows);
    }

    @Operation(summary = "抽奖：日限校验 → 权重随机 → 限量扣减 → 权益发放 → 落记录（事务）")
    @PostMapping("/{id}/draw")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> draw(@PathVariable("id") Long id, @RequestBody(required = false) DrawDTO dto) {
        Long memberId = requireLogin();
        MallLotteryActivity activity = lotteryActivityService.getById(id);
        LocalDateTime now = LocalDateTime.now();
        if (activity == null || !Integer.valueOf(1).equals(activity.getStatus())
                || now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new BusinessException(400, "活动不在进行中");
        }

        int dailyLimit = activity.getDailyLimit() == null ? 1 : activity.getDailyLimit();
        long used = todayUsed(memberId, id);
        if (used >= dailyLimit) {
            throw new BusinessException(400, "今日抽奖次数已用完");
        }

        LambdaQueryWrapper<MallLotteryPrize> prizeWrapper = new LambdaQueryWrapper<>();
        prizeWrapper.eq(MallLotteryPrize::getActivityId, id)
                .orderByAsc(MallLotteryPrize::getSort).orderByAsc(MallLotteryPrize::getId);
        List<MallLotteryPrize> prizes = lotteryPrizeService.list(prizeWrapper);
        MallLotteryPrize picked = weightedPick(prizes);
        if (picked == null) {
            throw new BusinessException(400, "奖品未配置");
        }

        // 副作用前预检：实物必须有收货地址；券不可用直接降级
        if (Integer.valueOf(2).equals(picked.getPrizeType()) && (dto == null || dto.getAddressId() == null)) {
            throw new BusinessException(400, "抽中实物奖品需选择收货地址");
        }
        MallLotteryPrize result = picked;
        MallCoupon coupon = null;
        if (Integer.valueOf(1).equals(result.getPrizeType())) {
            coupon = result.getRefId() == null ? null : mallCouponService.getById(result.getRefId());
            if (coupon == null || !Integer.valueOf(1).equals(coupon.getStatus())) {
                result = fallback(prizes);
                coupon = null;
            }
        }

        // 参与消耗积分（参与成本，与中奖与否无关；远程调用不随本地事务回滚）
        int consumePoints = activity.getConsumePoints() == null ? 0 : activity.getConsumePoints();
        if (consumePoints > 0) {
            PointGrantDTO consume = new PointGrantDTO();
            consume.setMemberId(memberId);
            consume.setChangeType(5);
            consume.setPoints(-consumePoints);
            consume.setRemark("抽奖消耗-" + activity.getName());
            memberInnerClient.grantPoints(consume);
        }

        // 限量奖品：条件更新扣减，扣不动（已抢光）降级谢谢参与
        if (result != null && result.getStock() != null && result.getStock() > -1) {
            int rows = jdbcTemplate.update(
                    "UPDATE mall_lottery_prize SET stock = stock - 1"
                            + " WHERE id = ? AND stock > 0 AND deleted = 0",
                    result.getId());
            if (rows == 0) {
                result = fallback(prizes);
                coupon = null;
            }
        }

        // 权益发放
        Long orderId = null;
        boolean win = result != null && !Integer.valueOf(3).equals(result.getPrizeType());
        if (win) {
            if (Integer.valueOf(0).equals(result.getPrizeType())) {
                PointGrantDTO grant = new PointGrantDTO();
                grant.setMemberId(memberId);
                grant.setChangeType(6);
                grant.setPoints(result.getRefId() == null ? 0 : result.getRefId().intValue());
                grant.setRemark("抽奖奖励-" + result.getName());
                memberInnerClient.grantPoints(grant);
            } else if (Integer.valueOf(1).equals(result.getPrizeType())) {
                int bumped = jdbcTemplate.update(
                        "UPDATE mall_coupon SET received_count = received_count + 1"
                                + " WHERE id = ? AND received_count < total_count AND deleted = 0",
                        coupon.getId());
                if (bumped == 0) {
                    // 券已抢光：降级谢谢参与（券库存竞态极小，无需再发其他权益）
                    result = fallback(prizes);
                    win = false;
                } else {
                    MallCouponRecord couponRecord = new MallCouponRecord();
                    couponRecord.setCouponId(coupon.getId());
                    couponRecord.setMemberId(memberId);
                    couponRecord.setStatus(1);
                    couponRecord.setReceivedTime(now);
                    couponRecord.setRemark("抽奖发放");
                    mallCouponRecordService.save(couponRecord);
                }
            } else if (Integer.valueOf(2).equals(result.getPrizeType())) {
                orderId = grantPhysical(result, memberId, dto.getAddressId());
            }
        }

        MallLotteryRecord record = new MallLotteryRecord();
        record.setMemberId(memberId);
        record.setActivityId(id);
        record.setPrizeId(result == null ? null : result.getId());
        record.setIsWin(win ? 1 : 0);
        record.setReceiveStatus(1);
        record.setOrderId(orderId);
        record.setDrawDate(LocalDate.now());
        lotteryRecordService.save(record);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("recordId", String.valueOf(record.getId()));
        data.put("isWin", win ? 1 : 0);
        data.put("prizeType", result == null ? 3 : result.getPrizeType());
        data.put("prizeName", result == null ? "谢谢参与" : result.getName());
        data.put("orderId", orderId == null ? null : String.valueOf(orderId));
        data.put("remain", Math.max(0, dailyLimit - (int) used - 1));
        return Result.ok(data);
    }

    @Operation(summary = "我的抽奖记录（含活动名/奖品名，最新 50 条）")
    @GetMapping("/my")
    public Result<List<Map<String, Object>>> my() {
        Long memberId = requireLogin();
        LambdaQueryWrapper<MallLotteryRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallLotteryRecord::getMemberId, memberId).orderByDesc(MallLotteryRecord::getId).last("LIMIT 50");
        List<MallLotteryRecord> records = lotteryRecordService.list(wrapper);

        List<Long> prizeIds = records.stream().map(MallLotteryRecord::getPrizeId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, MallLotteryPrize> prizeMap = prizeIds.isEmpty() ? Map.of()
                : lotteryPrizeService.listByIds(prizeIds).stream()
                        .collect(Collectors.toMap(MallLotteryPrize::getId, Function.identity()));
        List<Long> activityIds = records.stream().map(MallLotteryRecord::getActivityId).distinct().toList();
        Map<Long, MallLotteryActivity> activityMap = activityIds.isEmpty() ? Map.of()
                : lotteryActivityService.listByIds(activityIds).stream()
                        .collect(Collectors.toMap(MallLotteryActivity::getId, Function.identity()));

        List<Map<String, Object>> rows = records.stream().map(r -> {
            MallLotteryPrize p = r.getPrizeId() == null ? null : prizeMap.get(r.getPrizeId());
            MallLotteryActivity a = activityMap.get(r.getActivityId());
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", String.valueOf(r.getId()));
            m.put("activityId", String.valueOf(r.getActivityId()));
            m.put("activityName", a == null ? null : a.getName());
            m.put("prizeId", r.getPrizeId() == null ? null : String.valueOf(r.getPrizeId()));
            m.put("prizeName", p == null ? "谢谢参与" : p.getName());
            m.put("prizeType", p == null ? 3 : p.getPrizeType());
            m.put("isWin", r.getIsWin());
            m.put("receiveStatus", r.getReceiveStatus());
            m.put("orderId", r.getOrderId() == null ? null : String.valueOf(r.getOrderId()));
            m.put("createTime", r.getCreateTime());
            return m;
        }).toList();
        return Result.ok(rows);
    }

    @Operation(summary = "今日剩余次数")
    @GetMapping("/my/remain")
    public Result<Map<String, Object>> remain(@RequestParam("activityId") Long activityId) {
        Long memberId = requireLogin();
        MallLotteryActivity activity = lotteryActivityService.getById(activityId);
        if (activity == null) {
            throw new BusinessException(400, "活动不存在");
        }
        int dailyLimit = activity.getDailyLimit() == null ? 1 : activity.getDailyLimit();
        long used = todayUsed(memberId, activityId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("dailyLimit", dailyLimit);
        data.put("used", used);
        data.put("remain", Math.max(0, dailyLimit - (int) used));
        return Result.ok(data);
    }

    /** 实物奖品：解析商品默认 SKU → 建 order_type=3 零元单，返回订单ID */
    private Long grantPhysical(MallLotteryPrize prize, Long memberId, Long addressId) {
        List<SkuBrief> skus = goodsInnerClient.skuByGoods(prize.getRefId());
        if (skus == null || skus.isEmpty()) {
            throw new BusinessException(500, "实物奖品商品不可用");
        }
        SkuBrief sku = skus.get(0);
        String goodsName = prize.getName();
        List<GoodsBrief> goods = goodsInnerClient.goodsByIds(List.of(prize.getRefId()));
        if (goods != null && !goods.isEmpty()) {
            goodsName = goods.get(0).getName();
        }
        PromoOrderCreateDTO orderDto = new PromoOrderCreateDTO();
        orderDto.setMemberId(memberId);
        orderDto.setAddressId(addressId);
        orderDto.setGoodsId(prize.getRefId());
        orderDto.setSkuId(sku.getId());
        orderDto.setGoodsName(goodsName);
        orderDto.setSkuImage(sku.getImage());
        orderDto.setSpecJson(sku.getSpecJson());
        orderDto.setPrice(0L);
        orderDto.setNum(1);
        orderDto.setOrderType(3);
        orderDto.setBuyerRemark("抽奖实物奖品-" + prize.getName());
        OrderBrief order = orderInnerClient.createPromoOrder(orderDto);
        return order.getId();
    }

    /** 降级奖品：池中第一个「谢谢参与」（按 sort/id 序），无则 null */
    private MallLotteryPrize fallback(List<MallLotteryPrize> prizes) {
        return prizes.stream().filter(p -> Integer.valueOf(3).equals(p.getPrizeType()))
                .min(Comparator.comparing(MallLotteryPrize::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MallLotteryPrize::getId))
                .orElse(null);
    }

    /** 权重随机：只覆盖正权重奖品（weight=0 永不可中）；总权重<=0 视为未配置 */
    private MallLotteryPrize weightedPick(List<MallLotteryPrize> prizes) {
        int total = prizes.stream().mapToInt(p -> p.getWeight() == null ? 0 : Math.max(0, p.getWeight())).sum();
        if (total <= 0) {
            return null;
        }
        int rand = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;
        for (MallLotteryPrize p : prizes) {
            cumulative += p.getWeight() == null ? 0 : Math.max(0, p.getWeight());
            if (rand < cumulative) {
                return p;
            }
        }
        return null;
    }

    private long todayUsed(Long memberId, Long activityId) {
        LambdaQueryWrapper<MallLotteryRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallLotteryRecord::getMemberId, memberId)
                .eq(MallLotteryRecord::getActivityId, activityId)
                .eq(MallLotteryRecord::getDrawDate, LocalDate.now());
        return lotteryRecordService.count(wrapper);
    }

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }

    /** 抽奖请求体：addressId 仅抽中实物奖品时必填 */
    @Data
    public static class DrawDTO {
        private Long addressId;
    }
}
