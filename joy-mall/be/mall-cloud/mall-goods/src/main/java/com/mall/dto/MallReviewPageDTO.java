package com.mall.dto;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品评价分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallReviewPageDTO extends PageQuery {

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * 评分：1~5
     */
    private Integer rating;

    /**
     * 状态：0正常1后台隐藏
     */
    private Integer status;

    /**
     * 评价标签（V15）：精确匹配单个标签，如「物流快」
     */
    private String tag;
}
