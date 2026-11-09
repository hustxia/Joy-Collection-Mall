package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 收货地址
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_address")
public class MallAddress extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 会员ID 关联mall_member
     */
    private Long memberId;

    /**
     * 收货人
     */
    private String receiverName;

    /**
     * 联系电话
     */
    private String receiverPhone;

    /**
     * 省
     */
    private String province;

    /**
     * 市
     */
    private String city;

    /**
     * 区
     */
    private String district;

    /**
     * 详细地址
     */
    private String detail;

    /**
     * 默认地址:1默认0非默认
     */
    private Integer isDefault;

    /**
     * 备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====
}