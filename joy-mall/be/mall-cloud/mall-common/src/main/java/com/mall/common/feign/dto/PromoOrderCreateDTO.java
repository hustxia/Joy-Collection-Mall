package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 营销玩法建单请求（营销域 → 订单域）：秒杀/拼团/抽奖实物订单。
 * <p>价格由营销域活动快照决定，订单域不再重算；库存扣减在营销域活动维度完成，
 * 订单域只负责落单 + 明细 + 流转日志。
 */
@Data
public class PromoOrderCreateDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 下单会员 */
    private Long memberId;

    /** 收货地址 ID（订单域校验归属并做快照） */
    private Long addressId;

    private Long goodsId;

    private Long skuId;

    /** 商品名快照 */
    private String goodsName;

    private String skuImage;

    private String specJson;

    /** 成交单价（分）：秒杀价/拼团价/0（抽奖实物） */
    private Long price;

    /** 数量（秒杀/抽奖固定 1） */
    private Integer num;

    /** 订单类型：1秒杀 2拼团 3抽奖实物（见 mall_order.order_type） */
    private Integer orderType;

    private String buyerRemark;
}
