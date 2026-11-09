package com.mall.dto;

import lombok.Data;

/**
 * C 端订单操作入参：取消 / 确认收货。
 */
@Data
public class ShopOrderIdDTO {

    private Long orderId;
}
