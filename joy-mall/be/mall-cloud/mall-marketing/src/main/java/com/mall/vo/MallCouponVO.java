package com.mall.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券 VO
 */
@Data
public class MallCouponVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="vo-fields">
    private Long id;
    private String name;
    private Integer type;
    private Long amount;
    private Integer discountRate;
    private Long thresholdAmount;
    private Integer totalCount;
    private Integer receivedCount;
    private Integer perLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // <gen:end key="vo-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}