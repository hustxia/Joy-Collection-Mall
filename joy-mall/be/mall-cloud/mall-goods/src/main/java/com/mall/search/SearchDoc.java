package com.mall.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 搜索文档（V14）：搜索域内使用的商品扁平视图，只带检索与展示必需字段。
 *
 * <p>刻意不直接用 {@code MallGoods} 实体：排序/打分逻辑要能在纯单测里跑，
 * 不依赖 MyBatis 与数据库（见 {@code SearchFlowTest}）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchDoc {

    private Long id;
    /** 商品名 */
    private String name;
    /** 副标题 */
    private String subTitle;
    /** 品牌名（联表取，取不到为 null） */
    private String brandName;
    /** 类目名（联表取，取不到为 null） */
    private String categoryName;
    /** 类目ID：无结果兜底「同类热销」用 */
    private Long categoryId;
    /** 主图 */
    private String mainImage;
    /** 最低价（分） */
    private Long priceMin;
    /** 销量（排序次级因子） */
    private Integer sales;
}
