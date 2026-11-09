package com.mall.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * FAQ 知识库（V16）。
 *
 * <p>{@code code} 是幂等种子键：脚本重复执行只更新文案，不清零 {@code hitCount}。
 * 检索走「标准问题 + 同义问法」的关键词打分，不引入向量库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_faq")
public class Faq extends BaseEntity {

    private String code;

    private String question;

    private String answer;

    private String category;

    /** 同义问法，英文逗号分隔 */
    private String synonyms;

    private Long hitCount;

    /** 1 启用 0 停用 */
    private Integer status;
}
