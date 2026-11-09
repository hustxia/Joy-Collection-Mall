package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单流转日志
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_order_log")
public class MallOrderLog extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 订单ID 关联mall_order
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 变更内容
     */
    private String content;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 操作方:1会员2管理员3系统
     */
    private Integer operatorType;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}