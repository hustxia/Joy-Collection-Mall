package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 品牌
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_brand")
public class MallBrand extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 品牌名称
     */
    private String name;

    /**
     * 品牌LOGO URL
     */
    private String logo;

    /**
     * 品牌简介
     */
    private String intro;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 状态:1启用0停用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}