package com.mall.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 售后退款单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_refund")
public class MallRefund extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 售后单号
     */
    private String refundNo;

    /**
     * 订单ID 关联mall_order
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 售后类型:1仅退款2退货退款
     */
    private Integer type;

    /**
     * 退款金额(分)
     */
    private Long amount;

    /**
     * 申请原因
     */
    private String reason;

    /**
     * 凭证图URL JSON数组
     */
    private String evidenceImages;

    /**
     * 状态:1待审核2已同意3已拒绝4已退款5已关闭
     */
    private Integer status;

    /**
     * 审核意见
     */
    private String auditRemark;

    /**
     * 退款时间
     */
    private LocalDateTime refundTime;

    /**
     * 原支付单号 关联pay_order
     */
    private String payOrderNo;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /** V12 退款渠道：sandbox / wxpay / alipay（原路退回） */
    private String channel;

    /** V12 渠道退款单号（退款成功后回填，对账依据） */
    private String channelRefundNo;

    /** V12 渠道退款状态：0未发起 1退款中 2退款成功 3退款失败 */
    private Integer refundStatus;

    /** V15 关联订单项ID（部分退款/换货指向的订单项） */
    private Long orderItemId;

    /** V15 换货数量（type=3） */
    private Integer quantity;

    /** V15 换入SKU ID（type=3 换货，买家要换成的商品） */
    private Long exchangeSkuId;

    /** V15 买家寄回时间（type=3） */
    private LocalDateTime shipTime;

    /** V15 平台介入：0无 1仲裁中 2已强制退款 3已驳回 */
    private Integer platformStatus;

    /** V15 平台仲裁意见 */
    private String platformRemark;

    /** V15 买家申请平台介入时间 */
    private LocalDateTime interveneTime;
}