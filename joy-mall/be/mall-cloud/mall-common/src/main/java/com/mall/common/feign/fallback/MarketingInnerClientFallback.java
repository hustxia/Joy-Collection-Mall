package com.mall.common.feign.fallback;

import com.mall.common.exception.BusinessException;
import com.mall.common.feign.MarketingInnerClient;
import com.mall.common.feign.dto.CouponBrief;
import com.mall.common.feign.dto.CouponRecordBrief;
import com.mall.common.feign.dto.PromotionCalcRequest;
import com.mall.common.feign.dto.PromotionCalcResult;
import com.mall.common.feign.dto.PromotionHitWriteDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 营销域降级（V6 S-02）：熔断时读接口退化为「无可用券」（下单照常，只是用不了券），
 * 写接口（锁券/核销/释放）不降级——抛 503 让事务回滚，避免券被静默吞掉或重复核销。
 */
@Component
public class MarketingInnerClientFallback implements MarketingInnerClient {

    private static final String WRITE_MESSAGE = "营销服务暂不可用，请稍后重试";

    @Override
    public List<CouponRecordBrief> availableRecords(Long memberId) {
        // 读降级：当作没有可用券，不阻断下单主链
        return List.of();
    }

    @Override
    public List<CouponBrief> couponByIds(List<Long> ids) {
        return List.of();
    }

    @Override
    public Boolean lockRecord(Long recordId, Long orderId) {
        throw new BusinessException(503, WRITE_MESSAGE);
    }

    @Override
    public void useByOrder(Long orderId) {
        throw new BusinessException(503, WRITE_MESSAGE);
    }

    @Override
    public void releaseByOrder(Long orderId) {
        throw new BusinessException(503, WRITE_MESSAGE);
    }

    /**
     * V13 促销试算：营销域不可用时按「无优惠」降级——下单主链不能因为活动服务抖动而中断，
     * 少给优惠用户不会投诉，下不了单会（与运费试算降级为 0 同一取舍）。
     */
    @Override
    public PromotionCalcResult promotionCalc(PromotionCalcRequest request) {
        PromotionCalcResult result = new PromotionCalcResult();
        long total = 0L;
        if (request != null && request.getItems() != null) {
            for (PromotionCalcRequest.Item item : request.getItems()) {
                long price = item.getPrice() == null ? 0L : item.getPrice();
                int qty = item.getQuantity() == null ? 0 : item.getQuantity();
                total += price * qty;
                PromotionCalcResult.Line line = new PromotionCalcResult.Line();
                line.setSkuId(item.getSkuId());
                line.setGoodsId(item.getGoodsId());
                line.setMerchantId(item.getMerchantId());
                line.setOriginalAmount(price * qty);
                line.setPayableAmount(price * qty);
                result.getLines().add(line);
            }
        }
        result.setTotalAmount(total);
        result.setPayableAmount(total);
        result.setPromotionDiscount(0L);
        result.setStackMode("best");
        result.setTip("营销服务暂不可用，本次活动优惠未生效");
        return result;
    }

    /** 命中留痕属于旁路记录：营销域不可用时静默丢弃，不能让已扣库存的订单事务回滚 */
    @Override
    public void savePromotionHits(PromotionHitWriteDTO dto) {
        // no-op
    }

    @Override
    public Map<String, Object> nextTier(Long memberId, Long amount, Long merchantId) {
        return Map.of("totalAmount", amount == null ? 0L : amount, "gap", 0L, "discount", 0L, "tip", "");
    }
}
