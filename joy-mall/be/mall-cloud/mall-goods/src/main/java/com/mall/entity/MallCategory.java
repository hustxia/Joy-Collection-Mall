package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品类目
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_category")
public class MallCategory extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 父类目ID 0=顶级
     */
    private Long parentId;

    /**
     * 类目名称
     */
    private String name;

    /**
     * 层级:1一级2二级3三级
     */
    private Integer level;

    /**
     * 类目图标URL
     */
    private String icon;

    /**
     * 排序 越小越靠前
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