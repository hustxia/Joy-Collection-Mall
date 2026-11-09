package com.mall.common.feign;

import com.mall.common.feign.dto.CouponBrief;
import com.mall.common.feign.dto.CouponRecordBrief;
import com.mall.common.feign.dto.PromotionCalcRequest;
import com.mall.common.feign.dto.PromotionCalcResult;
import com.mall.common.feign.dto.PromotionHitWriteDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 营销域内部客户端：订单域创建订单时读取可用优惠券，并在下单/支付/取消各环节锁定、核销、释放优惠券。
 * <p>静态地址：url 取 feign.clients.mall-marketing，缺省 http://localhost:8205。
 */
@FeignClient(name = "mall-marketing", url = "${feign.clients.mall-marketing:http://localhost:8205}",
        fallback = com.mall.common.feign.fallback.MarketingInnerClientFallback.class)
public interface MarketingInnerClient {

    /** 会员当前可用（已领取、未使用、未绑定订单）的优惠券记录 */
    @GetMapping("/inner/coupon-record")
    List<CouponRecordBrief> availableRecords(@RequestParam("memberId") Long memberId);

    @GetMapping("/inner/coupon/by-ids")
    List<CouponBrief> couponByIds(@RequestParam("ids") List<Long> ids);

    /**
     * 提交订单时锁定券：条件 UPDATE（仅 status=1 且 order_id 为空可锁定），返回是否成功。
     * 锁定仅占位 order_id，status 保持 1，支付成功才置 2（已用）。
     */
    @PostMapping("/inner/coupon-record/lock")
    Boolean lockRecord(@RequestParam("recordId") Long recordId,
                       @RequestParam("orderId") Long orderId);

    /** 支付成功：将该订单下所有 status=1 的记录置为已用（status=2） */
    @PostMapping("/inner/coupon-record/use-by-order")
    void useByOrder(@RequestParam("orderId") Long orderId);

    /** 取消/关闭订单：释放该订单锁定的券（order_id 置空，status 回 1） */
    @PostMapping("/inner/coupon-record/release-by-order")
    void releaseByOrder(@RequestParam("orderId") Long orderId);

    /**
     * V13 促销试算：订单确认页与下单提交调同一个入口，保证「预览价 = 成交价」。
     * <p>只算活动，不含券/积分/运费（它们由订单域在活动之后依次叠加）。
     */
    @PostMapping("/inner/promotion/calc")
    PromotionCalcResult promotionCalc(@RequestBody PromotionCalcRequest request);

    /** V13 促销命中留痕：订单创建成功后回写，过期/未命中的活动不写 */
    @PostMapping("/inner/promotion/hits")
    void savePromotionHits(@RequestBody PromotionHitWriteDTO dto);

    /**
     * V13 购物车凑单提示：给定已勾选金额，返回「再买 X 元可减 Y」与「可用券」提示。
     * <p>返回 {totalAmount, gap, discount, name, tip, couponTip, couponName}。
     */
    @GetMapping("/inner/promotion/next-tier")
    Map<String, Object> nextTier(@RequestParam(value = "memberId", required = false) Long memberId,
                                 @RequestParam("amount") Long amount,
                                 @RequestParam(value = "merchantId", required = false) Long merchantId);
}
