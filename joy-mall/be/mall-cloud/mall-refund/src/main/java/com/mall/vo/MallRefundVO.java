package com.mall.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 售后退款单 VO
 */
@Data
public class MallRefundVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="vo-fields">
    private Long id;
    private String refundNo;
    private Long orderId;
    private String orderNo;
    private Long memberId;
    private Integer type;
    private Long amount;
    private String reason;
    private String evidenceImages;
    private Integer status;
    private String auditRemark;
    private LocalDateTime refundTime;
    private String payOrderNo;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // <gen:end key="vo-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /** V4 售后闭环：会员展示信息（page 批量回填，非表字段） */
    private String memberNickname;
    private String memberPhone;

    /** V15 关联订单项ID（部分退款/换货） */
    private Long orderItemId;
    /** V15 换货数量（type=3） */
    private Integer quantity;
    /** V15 换入SKU ID（type=3） */
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