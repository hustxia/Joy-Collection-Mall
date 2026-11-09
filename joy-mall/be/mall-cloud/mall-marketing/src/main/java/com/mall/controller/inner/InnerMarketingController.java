package com.mall.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.feign.dto.CouponBrief;
import com.mall.common.feign.dto.CouponRecordBrief;
import com.mall.common.feign.dto.PromotionCalcRequest;
import com.mall.common.feign.dto.PromotionCalcResult;
import com.mall.common.feign.dto.PromotionHitWriteDTO;
import com.mall.entity.MallCoupon;
import com.mall.entity.MallCouponRecord;
import com.mall.service.MallCommissionRecordService;
import com.mall.service.MallCouponRecordService;
import com.mall.service.MallCouponService;
import com.mall.service.MallPromotionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 营销域内部端点（/inner/**，免鉴权）：供订单域读取可用券、按订单锁定/核销/释放优惠券，
 * 以及 V13 的促销试算与命中留痕。
 *
 * <p>内部 RPC 直接返回原始载荷（不套 Result 壳）。
 */
@RestController
@RequestMapping("/inner")
public class InnerMarketingController {

    private final MallCouponRecordService mallCouponRecordService;
    private final MallCouponService mallCouponService;
    private final MallCommissionRecordService commissionRecordService;
    private final MallPromotionService promotionService;

    public InnerMarketingController(MallCouponRecordService mallCouponRecordService,
                                    MallCouponService mallCouponService,
                                    MallCommissionRecordService commissionRecordService,
                                    MallPromotionService promotionService) {
        this.mallCouponRecordService = mallCouponRecordService;
        this.mallCouponService = mallCouponService;
        this.commissionRecordService = commissionRecordService;
        this.promotionService = promotionService;
    }

    @GetMapping("/coupon-record")
    public List<CouponRecordBrief> availableRecords(@RequestParam("memberId") Long memberId) {
        LambdaQueryWrapper<MallCouponRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallCouponRecord::getMemberId, memberId)
                .eq(MallCouponRecord::getStatus, 1)
                .isNull(MallCouponRecord::getOrderId);
        return mallCouponRecordService.list(wrapper).stream().map(this::toRecordBrief).toList();
    }

    @GetMapping("/coupon/by-ids")
    public List<CouponBrief> couponByIds(@RequestParam("ids") List<Long> ids) {
        return mallCouponService.listByIds(ids).stream().map(this::toCouponBrief).toList();
    }

    @PostMapping("/coupon-record/lock")
    public Boolean lockRecord(@RequestParam("recordId") Long recordId,
                              @RequestParam("orderId") Long orderId) {
        return mallCouponRecordService.lockRecord(recordId, orderId);
    }

    @PostMapping("/coupon-record/use-by-order")
    public void useByOrder(@RequestParam("orderId") Long orderId) {
        mallCouponRecordService.useByOrder(orderId);
    }

    @PostMapping("/coupon-record/release-by-order")
    public void releaseByOrder(@RequestParam("orderId") Long orderId) {
        mallCouponRecordService.releaseByOrder(orderId);
    }

    /**
     * V13 促销试算（订单确认页与下单提交共用）：只算活动，券/积分/运费由订单域在其后叠加。
     */
    @PostMapping("/promotion/calc")
    public PromotionCalcResult promotionCalc(@RequestBody PromotionCalcRequest request) {
        return promotionService.calculate(request);
    }

    /** V13 促销命中留痕：订单创建成功后回写，0 元命中与过期活动都不写 */
    @PostMapping("/promotion/hits")
    public void savePromotionHits(@RequestBody PromotionHitWriteDTO dto) {
        promotionService.writeHits(dto);
    }

    /** V13 购物车凑单提示：再买 X 元可减 Y / 可用券 Z */
    @GetMapping("/promotion/next-tier")
    public Map<String, Object> nextTier(@RequestParam(value = "memberId", required = false) Long memberId,
                                        @RequestParam("amount") Long amount,
                                        @RequestParam(value = "merchantId", required = false) Long merchantId) {
        return promotionService.nextTier(memberId, amount == null ? 0L : amount, merchantId);
    }

    private CouponRecordBrief toRecordBrief(MallCouponRecord r) {
        CouponRecordBrief b = new CouponRecordBrief();
        b.setId(r.getId());
        b.setCouponId(r.getCouponId());
        b.setMemberId(r.getMemberId());
        b.setOrderId(r.getOrderId());
        b.setStatus(r.getStatus());
        b.setReceivedTime(r.getReceivedTime());
        b.setUsedTime(r.getUsedTime());
        return b;
    }

    private CouponBrief toCouponBrief(MallCoupon c) {
        CouponBrief b = new CouponBrief();
        b.setId(c.getId());
        b.setName(c.getName());
        b.setType(c.getType());
        b.setAmount(c.getAmount());
        b.setDiscountRate(c.getDiscountRate());
        b.setThresholdAmount(c.getThresholdAmount());
        b.setStartTime(c.getStartTime());
        b.setEndTime(c.getEndTime());
        b.setStatus(c.getStatus());
        return b;
    }

    /**
     * V9 订单完成结算佣金（供 order 域订单完成钩子调用，幂等）：按规则计算 → 落记录 → 累加分销员与会员余额。
     */
    @PostMapping("/marketing/distributor-settle")
    public void distributorSettle(@RequestBody DistributorSettleDTO dto) {
        if (dto == null) {
            return;
        }
        commissionRecordService.settle(dto.getDistributorId(), dto.getOrderId(),
                dto.getMemberId(), dto.getGoodsId(), dto.getPayAmount());
    }

    /**
     * V9 退款失效佣金（供 refund 域退款钩子调用）：置失效并回退分销员可提现与会员余额。
     */
    @PostMapping("/marketing/commission-invalid")
    public void commissionInvalid(@RequestBody CommissionInvalidDTO dto) {
        if (dto == null) {
            return;
        }
        commissionRecordService.invalid(dto.getOrderId(), dto.getDistributorId());
    }

    @lombok.Data
    public static class DistributorSettleDTO {
        private Long distributorId;
        private Long orderId;
        private Long memberId;
        private Long goodsId;
        private Long payAmount;
    }

    @lombok.Data
    public static class CommissionInvalidDTO {
        private Long orderId;
        private Long distributorId;
    }
}
