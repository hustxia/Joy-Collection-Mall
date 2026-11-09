package com.mall.pay;

import com.mall.common.exception.BusinessException;
import com.mall.entity.MallPayment;

import java.util.Map;

/**
 * 未配置的真实渠道占位（V12）：微信 / 支付宝需要商户号、API 证书、平台公钥，
 * 未配置时一律 {@code configured()=false}，调用直接 503。
 *
 * <p>为什么不做「假装成功」的桩：支付是这个系统唯一真正收钱的地方，
 * 一个会返回成功的假渠道，比没有渠道更危险（对账、退款、结算全部会跟着错）。
 */
public abstract class UnconfiguredPayChannel implements PayChannel {

    private final String code;
    private final String hint;

    protected UnconfiguredPayChannel(String code, String hint) {
        this.code = code;
        this.hint = hint;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public boolean configured() {
        return false;
    }

    @Override
    public Map<String, Object> prepay(MallPayment payment) {
        throw new BusinessException(503, hint);
    }

    @Override
    public PayNotify verifyNotify(String rawBody) {
        throw new BusinessException(503, hint);
    }
}
