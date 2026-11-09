package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 购物车项精简视图（会员域 → 订单域 内部传输）
 */
@Data
public class CartBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long memberId;
    private Long skuId;
    private Integer num;
    private Integer checked;
}
