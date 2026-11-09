package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 搜索词表（V14）：中文词 / 全拼 / 首字母 / 同义词组，替代 ES 词典的过渡方案。
 *
 * <p>只做「正向查表」：词 → 同义词，拼音 → 词；不对商品标题反向注音（离线无注音库）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_search_term")
public class MallSearchTerm extends BaseEntity {

    /** 词（中文词或英文词） */
    private String word;

    /** 全拼，小写无分隔（shouji） */
    private String pinyin;

    /** 拼音首字母，小写（sj） */
    private String pinyinInitial;

    /** 同义词组，逗号分隔（移动电话,智能手机） */
    private String synonyms;

    /** 关联类目ID：无结果时「同类热销」兜底 */
    private Long categoryId;

    /** 权重：热搜兜底排序，越大越靠前 */
    private Integer weight;

    /** 状态：1启用0停用 */
    private Integer status;
}
