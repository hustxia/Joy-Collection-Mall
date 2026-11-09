package com.mall.vo;

import lombok.Data;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品SKU VO
 */
@Data
public class MallSkuVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // <gen:begin key="vo-fields">
    private Long id;
    private Long goodsId;
    private String skuCode;
    private String specJson;
    private Long price;
    private Integer stock;
    private Integer sales;
    private String image;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // <gen:end key="vo-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /** V12 重量（克）：按重量计费依据 */
    private Integer weight;
}