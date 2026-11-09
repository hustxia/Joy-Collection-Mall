package com.mall.common.feign.fallback;

import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.dto.FreightQuoteRequest;
import com.mall.common.feign.dto.FreightQuoteResult;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.SkuBrief;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 商品域降级（V6 S-02）：熔断打开后直接抛出可读的 503 业务异常，<b>绝不伪造成功</b>。
 *
 * <p>特别注意写方法：{@code deductStock} 若降级返回 true，订单会以为扣减成功而继续下单，
 * 造成超卖；所以所有方法一律抛异常，让调用方事务回滚并给用户看「服务暂不可用」。
 */
@Component
public class GoodsInnerClientFallback implements GoodsInnerClient {

    private static final String MESSAGE = "商品服务暂不可用，请稍后重试";

    @Override
    public List<SkuBrief> skuByIds(List<Long> ids) {
        throw new BusinessException(503, MESSAGE);
    }

    @Override
    public List<GoodsBrief> goodsByIds(List<Long> ids) {
        throw new BusinessException(503, MESSAGE);
    }

    @Override
    public List<SkuBrief> skuByGoods(Long goodsId) {
        throw new BusinessException(503, MESSAGE);
    }

    @Override
    public Boolean deductStock(Map<Long, Integer> deltas) {
        throw new BusinessException(503, MESSAGE);
    }

    @Override
    public void restoreStock(Map<Long, Integer> deltas) {
        throw new BusinessException(503, MESSAGE);
    }

    @Override
    public void incrementSkuSales(Map<Long, Integer> deltas) {
        throw new BusinessException(503, MESSAGE);
    }

    @Override
    public void incrementGoodsSales(Map<Long, Integer> deltas) {
        throw new BusinessException(503, MESSAGE);
    }

    /**
     * 运费试算降级：抛 503 而非返回 0。
     * <p>返回 0 会让用户以为包邮、下单后再改价，属于「伪造成功」；宁可拦住下单。
     */
    @Override
    public FreightQuoteResult freightQuote(FreightQuoteRequest request) {
        throw new BusinessException(503, MESSAGE);
    }
}
