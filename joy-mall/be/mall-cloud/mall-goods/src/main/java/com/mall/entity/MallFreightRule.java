package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运费模板地区规则（V12）。
 *
 * <p>首件/续件的单位随模板计费方式变化：按件计费时单位是「件」，按重量计费时单位是「克」。
 * {@code regionCodes} 为空表示该模板的默认规则（未命中任何精确地区时使用）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_freight_rule")
public class MallFreightRule extends BaseEntity {

    /** 模板ID */
    private Long templateId;

    /** 适用地区：逗号分隔的省份名（如「新疆,西藏」）；空=模板默认规则 */
    private String regionCodes;

    /** 首件/首重：按件为件数，按重量为克 */
    private Integer firstUnit;

    /** 首件/首重运费（分） */
    private Long firstFee;

    /** 续件/续重步长 */
    private Integer extraUnit;

    /** 每续一件/一续重运费（分） */
    private Long extraFee;

    /** 满额免邮阈值（分），0=不启用 */
    private Long freeThreshold;

    /** 排序：越小越先匹配 */
    private Integer sort;
}
