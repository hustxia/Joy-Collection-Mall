package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 运费模板（V12）：一个模板下挂多条地区规则，按地区精确匹配后回落默认规则。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_freight_template")
public class MallFreightTemplate extends BaseEntity {

    /** 计费方式：按件 */
    public static final int CHARGE_BY_PIECE = 1;
    /** 计费方式：按重量（克） */
    public static final int CHARGE_BY_WEIGHT = 2;
    /** 计费方式：满额包邮（未达门槛收固定首费） */
    public static final int CHARGE_FREE_OVER = 3;

    /** 模板名称 */
    private String name;

    /** 计费方式：1按件2按重量3满额包邮 */
    private Integer chargeType;

    /** 是否全局默认：1是0否（同库唯一由业务保证，设置时互斥） */
    private Integer isDefault;

    /** 状态：1启用0停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    public static String chargeTypeText(Integer type) {
        if (type == null) {
            return "-";
        }
        return switch (type) {
            case CHARGE_BY_PIECE -> "按件";
            case CHARGE_BY_WEIGHT -> "按重量";
            case CHARGE_FREE_OVER -> "满额包邮";
            default -> String.valueOf(type);
        };
    }
}
