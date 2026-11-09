package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 搜索日志（V14）：热搜词统计与无结果分析的数据源。
 *
 * <p>只记「词 + 结果数 + 是否改写」，不记原始查询串以外的隐私信息；
 * 写入失败绝不能影响搜索本身（service 侧 catch 后仅打 WARN）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_search_log")
public class MallSearchLog extends BaseEntity {

    /** 规范化后的搜索词 */
    private String keyword;

    /** 会员ID（未登录为空） */
    private Long memberId;

    /** 命中条数 */
    private Integer resultCount;

    /** 是否有结果：1是0否 */
    private Integer hit;

    /** 是否由拼音命中：1是0否 */
    private Integer pinyinHit;

    /** 纠错前的原词（未纠错为空） */
    private String correctedFrom;
}
