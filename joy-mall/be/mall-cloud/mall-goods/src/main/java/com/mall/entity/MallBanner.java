package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 轮播图
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_banner")
public class MallBanner extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 标题
     */
    private String title;

    /**
     * 图片URL
     */
    private String image;

    /**
     * 跳转链接
     */
    private String linkUrl;

    /**
     * 投放位:1首页轮播2分类页
     */
    private Integer position;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 状态:1显示0隐藏
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}