package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商家精简视图（商家域 → 商品/订单域 内部传输）
 */
@Data
public class MerchantBrief implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String merchantNo;
    /** 店铺名（C 端展示） */
    private String name;
    private String logo;
    private String intro;
    private String category;
    /** 状态：0待审1启用2停用3驳回 */
    private Integer status;
}
