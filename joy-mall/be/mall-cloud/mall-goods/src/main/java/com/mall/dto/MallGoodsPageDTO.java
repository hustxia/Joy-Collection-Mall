package com.mall.dto;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallGoodsPageDTO extends PageQuery {

    // <gen:begin key="dto-fields">
    // <gen:end key="dto-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /** V10：按商家筛选（C 端店铺页 / 后台商家维度） */
    private Long merchantId;
}