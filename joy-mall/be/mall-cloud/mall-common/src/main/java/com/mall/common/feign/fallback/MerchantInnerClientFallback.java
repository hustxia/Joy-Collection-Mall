package com.mall.common.feign.fallback;

import com.mall.common.exception.BusinessException;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.common.feign.dto.MerchantBrief;
import org.springframework.stereotype.Component;

/**
 * 商家域降级（V10）：读接口退化为「无店铺信息」，展示侧回落 null 不阻断页面；
 * 写接口（结算/冲减）<b>不降级</b>——抛 503 让调用方明确知道本次没入账，
 * 由调用方「记日志 + 后续补结算」处理，绝不静默吞掉商家货款。
 */
@Component
public class MerchantInnerClientFallback implements MerchantInnerClient {

    private static final String WRITE_MESSAGE = "商家服务暂不可用，结算未入账，请稍后补结算";

    @Override
    public MerchantBrief merchant(Long id) {
        return null;
    }

    @Override
    public Boolean settle(Long orderId, Long merchantId, String orderNo, Long payAmount) {
        throw new BusinessException(503, WRITE_MESSAGE);
    }

    @Override
    public Boolean refundAdjust(Long refundId, Long orderId, Long merchantId, Long refundAmount) {
        throw new BusinessException(503, WRITE_MESSAGE);
    }
}
