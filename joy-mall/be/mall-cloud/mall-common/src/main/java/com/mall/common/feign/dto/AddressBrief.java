package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 收货地址精简视图（会员域 → 订单域 内部传输）
 */
@Data
public class AddressBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long memberId;
    private String receiverName;
    private String receiverPhone;
    private String province;
    private String city;
    private String district;
    private String detail;
    private Integer isDefault;
}
