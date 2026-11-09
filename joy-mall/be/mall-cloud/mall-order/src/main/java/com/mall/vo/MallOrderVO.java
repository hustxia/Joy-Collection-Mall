package com.mall.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单 VO
 */
@Data
public class MallOrderVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="vo-fields">
    private Long id;
    private String orderNo;
    private Long memberId;
    private Long totalAmount;
    private Long freightAmount;
    private Long discountAmount;
    private Long payAmount;
    private Integer status;
    private Integer payStatus;
    private Integer payType;
    private String payOrderNo;
    private LocalDateTime payTime;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String expressCompany;
    private String expressNo;
    private LocalDateTime deliverTime;
    private LocalDateTime finishTime;
    private String buyerRemark;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // <gen:end key="vo-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}