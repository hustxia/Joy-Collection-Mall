package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 支付单（V12）：一次「下单支付尝试」一行，与订单是多对一。
 *
 * <p>幂等口径：同一订单在同一渠道只复用一张未支付的支付单；
 * 回调以 {@code pay_no} 定位并做条件更新，重复回调不会二次推进订单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_payment")
public class MallPayment extends BaseEntity {

    /** 状态：待支付 */
    public static final int ST_WAIT = 0;
    /** 状态：支付成功 */
    public static final int ST_SUCCESS = 1;
    /** 状态：支付失败 */
    public static final int ST_FAIL = 2;
    /** 状态：已全额退款 */
    public static final int ST_REFUNDED = 3;
    /** 状态：部分退款 */
    public static final int ST_PART_REFUNDED = 4;

    private String payNo;

    private Long orderId;

    private String orderNo;

    private Long memberId;

    private Long merchantId;

    /** sandbox / wxpay / alipay */
    private String channel;

    /** NATIVE / JSAPI / H5 / MOCK */
    private String tradeType;

    private Long amount;

    private Integer status;

    private String channelTradeNo;

    private String prepayParams;

    private LocalDateTime notifyTime;

    private String rawNotify;

    private LocalDateTime expireTime;

    public static String statusText(Integer status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case ST_WAIT -> "待支付";
            case ST_SUCCESS -> "支付成功";
            case ST_FAIL -> "支付失败";
            case ST_REFUNDED -> "已全额退款";
            case ST_PART_REFUNDED -> "部分退款";
            default -> String.valueOf(status);
        };
    }
}
