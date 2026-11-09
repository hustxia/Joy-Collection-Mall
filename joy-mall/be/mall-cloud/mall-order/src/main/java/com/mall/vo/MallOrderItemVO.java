package com.mall.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单明细 VO
 */
@Data
public class MallOrderItemVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="vo-fields">
    private Long id;
    private Long orderId;
    private Long goodsId;
    private Long skuId;
    private String goodsName;
    private String skuImage;
    private String specJson;
    private Long price;
    private Integer quantity;
    private Long totalAmount;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // <gen:end key="vo-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}