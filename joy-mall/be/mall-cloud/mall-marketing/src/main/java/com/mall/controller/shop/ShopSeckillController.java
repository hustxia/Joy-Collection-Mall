package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.OrderInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.PromoOrderCreateDTO;
import com.mall.common.feign.dto.SkuBrief;
import com.mall.common.result.Result;
import com.mall.entity.MallSeckillActivity;
import com.mall.entity.MallSeckillRecord;
import com.mall.service.MallSeckillActivityService;
import com.mall.service.MallSeckillRecordService;
import com.mall.service.SeckillStockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C 端秒杀（V8）：活动列表/详情/抢购。
 *
 * <p>抢购链路：时间窗校验 → 限购校验 → Redis Lua 原子预扣（不可用降级）→
 * DB available_stock 乐观锁（最终闸门）→ 参与记录（uk 防重）→ 订单域建单（order_type=1）。
 * 任一步失败整体回滚：DB 走事务，Redis 手动 INCR 补偿。
 */
@Tag(name = "商城前台-秒杀", description = "秒杀活动/抢购")
@RestController
@RequestMapping("/shop/seckill")
public class ShopSeckillController {

    private final MallSeckillActivityService seckillActivityService;
    private final MallSeckillRecordService seckillRecordService;
    private final SeckillStockService seckillStockService;
    private final GoodsInnerClient goodsInnerClient;
    private final OrderInnerClient orderInnerClient;
    /** 只用于本域 mall_seckill_activity 的乐观锁扣减，不跨域 */
    private final JdbcTemplate jdbcTemplate;

    public ShopSeckillController(MallSeckillActivityService seckillActivityService,
                                 MallSeckillRecordService seckillRecordService,
                                 SeckillStockService seckillStockService,
                                 GoodsInnerClient goodsInnerClient,
                                 OrderInnerClient orderInnerClient,
                                 JdbcTemplate jdbcTemplate) {
        this.seckillActivityService = seckillActivityService;
        this.seckillRecordService = seckillRecordService;
        this.seckillStockService = seckillStockService;
        this.goodsInnerClient = goodsInnerClient;
        this.orderInnerClient = orderInnerClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "秒杀活动列表：scope=ongoing 进行中（默认）/ upcoming 即将开始")
    @GetMapping("/activities")
    public Result<List<Map<String, Object>>> activities(
            @RequestParam(defaultValue = "ongoing") String scope) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<MallSeckillActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallSeckillActivity::getStatus, 1);
        if ("upcoming".equals(scope)) {
            // 即将开始：未开抢（开始时间在未来），按开抢时间升序，先开抢的排前面
            wrapper.gt(MallSeckillActivity::getStartTime, now)
                    .orderByAsc(MallSeckillActivity::getStartTime);
        } else {
            wrapper.le(MallSeckillActivity::getStartTime, now)
                    .ge(MallSeckillActivity::getEndTime, now)
                    .orderByDesc(MallSeckillActivity::getId);
        }
        List<MallSeckillActivity> activities = seckillActivityService.list(wrapper);
        return Result.ok(toRows(activities, UserContext.getUserId()));
    }

    @Operation(summary = "秒杀活动详情（含我已购数量）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        MallSeckillActivity activity = seckillActivityService.getById(id);
        if (activity == null) {
            throw new BusinessException(400, "活动不存在");
        }
        List<Map<String, Object>> rows = toRows(List.of(activity), UserContext.getUserId());
        return Result.ok(rows.get(0));
    }

    @Operation(summary = "抢购：限购 + Redis 预扣 + DB 乐观锁 + 建单（事务）")
    @PostMapping("/{id}/buy")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> buy(@PathVariable("id") Long id, @RequestBody BuyDTO dto) {
        Long memberId = UserContext.getUserId();
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (dto == null || dto.getAddressId() == null) {
            throw new BusinessException(400, "请选择收货地址");
        }
        MallSeckillActivity activity = seckillActivityService.getById(id);
        LocalDateTime now = LocalDateTime.now();
        if (activity == null || !Integer.valueOf(1).equals(activity.getStatus())
                || now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new BusinessException(400, "活动不在进行中");
        }
        int perLimit = activity.getPerLimit() == null ? 1 : activity.getPerLimit();
        if (myBoughtCount(id, memberId) >= perLimit) {
            throw new BusinessException(400, "已达限购数量");
        }

        boolean redisDeducted = false;
        try {
            SeckillStockService.DeductResult result =
                    seckillStockService.tryDeduct(id, activity.getAvailableStock());
            if (result == SeckillStockService.DeductResult.SOLD_OUT) {
                throw new BusinessException(400, "手慢了，已抢光");
            }
            redisDeducted = result == SeckillStockService.DeductResult.OK;

            // DB 乐观锁是最终闸门：Redis 与 DB 不一致时以 DB 为准
            int rows = jdbcTemplate.update(
                    "UPDATE mall_seckill_activity SET available_stock = available_stock - 1"
                            + " WHERE id = ? AND available_stock > 0 AND deleted = 0", id);
            if (rows == 0) {
                throw new BusinessException(400, "手慢了，已抢光");
            }

            MallSeckillRecord record = new MallSeckillRecord();
            record.setActivityId(id);
            record.setMemberId(memberId);
            record.setSkuId(activity.getSkuId());
            try {
                seckillRecordService.save(record);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(400, "请勿重复购买");
            }

            OrderBrief order = createPromoOrder(activity, memberId, dto);
            record.setOrderId(order.getId());
            seckillRecordService.updateById(record);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("orderId", String.valueOf(order.getId()));
            data.put("orderNo", order.getOrderNo());
            data.put("payAmount", order.getPayAmount());
            return Result.ok(data);
        } catch (RuntimeException e) {
            // 事务回滚负责 DB 库存与记录；Redis 预扣不在事务内，手动补偿
            if (redisDeducted) {
                seckillStockService.compensate(id);
            }
            throw e;
        }
    }

    /** 订单域建单（营销域定价格快照）；远程失败统一转业务异常触发回滚 */
    private OrderBrief createPromoOrder(MallSeckillActivity activity, Long memberId, BuyDTO dto) {
        PromoOrderCreateDTO req = new PromoOrderCreateDTO();
        req.setMemberId(memberId);
        req.setAddressId(dto.getAddressId());
        req.setGoodsId(activity.getGoodsId());
        req.setSkuId(activity.getSkuId());
        req.setPrice(activity.getSeckillPrice());
        req.setNum(1);
        req.setOrderType(1);
        req.setBuyerRemark(dto.getBuyerRemark());
        List<SkuBrief> skus = goodsInnerClient.skuByIds(List.of(activity.getSkuId()));
        SkuBrief sku = skus == null || skus.isEmpty() ? null : skus.get(0);
        List<GoodsBrief> goods = goodsInnerClient.goodsByIds(List.of(activity.getGoodsId()));
        GoodsBrief g = goods == null || goods.isEmpty() ? null : goods.get(0);
        req.setGoodsName(g == null ? null : g.getName());
        req.setSkuImage(sku != null && sku.getImage() != null ? sku.getImage()
                : g == null ? null : g.getMainImage());
        req.setSpecJson(sku == null ? null : sku.getSpecJson());
        try {
            OrderBrief order = orderInnerClient.createPromoOrder(req);
            if (order == null || order.getId() == null) {
                throw new BusinessException(500, "下单失败，请稍后重试");
            }
            return order;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "下单失败，请稍后重试");
        }
    }

    private long myBoughtCount(Long activityId, Long memberId) {
        LambdaQueryWrapper<MallSeckillRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallSeckillRecord::getActivityId, activityId)
                .eq(MallSeckillRecord::getMemberId, memberId);
        return seckillRecordService.count(wrapper);
    }

    /** 活动列表/详情统一装配：商品名/图经商品域内部接口补齐 */
    private List<Map<String, Object>> toRows(List<MallSeckillActivity> activities, Long memberId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (activities.isEmpty()) {
            return rows;
        }
        List<Long> goodsIds = activities.stream().map(MallSeckillActivity::getGoodsId).distinct().toList();
        List<GoodsBrief> briefs = goodsInnerClient.goodsByIds(goodsIds);
        Map<Long, GoodsBrief> goodsMap = briefs == null ? Map.of()
                : briefs.stream().collect(Collectors.toMap(GoodsBrief::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        for (MallSeckillActivity a : activities) {
            GoodsBrief g = goodsMap.get(a.getGoodsId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(a.getId()));
            m.put("goodsId", String.valueOf(a.getGoodsId()));
            m.put("skuId", String.valueOf(a.getSkuId()));
            m.put("goodsName", g == null ? null : g.getName());
            m.put("mainImage", g == null ? null : g.getMainImage());
            m.put("originPrice", a.getOriginPrice());
            m.put("seckillPrice", a.getSeckillPrice());
            m.put("totalStock", a.getTotalStock());
            m.put("availableStock", a.getAvailableStock());
            m.put("perLimit", a.getPerLimit());
            m.put("startTime", a.getStartTime());
            m.put("endTime", a.getEndTime());
            m.put("onSale", Integer.valueOf(1).equals(a.getStatus())
                    && !now.isBefore(a.getStartTime()) && !now.isAfter(a.getEndTime()));
            m.put("myCount", memberId == null ? 0 : myBoughtCount(a.getId(), memberId));
            rows.add(m);
        }
        return rows;
    }

    @Data
    public static class BuyDTO {
        private Long addressId;
        private String buyerRemark;
    }
}
