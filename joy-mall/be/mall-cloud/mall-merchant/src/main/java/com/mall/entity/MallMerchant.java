package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商家（店铺）——V10 商户入驻主体。
 *
 * <p>status：0 待审 / 1 启用 / 2 停用 / 3 驳回；settle_rate 为平台抽成费率（‰）。
 * <p>id=1 是系统保留的「平台自营店铺」，历史商品与订单全部归它。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_merchant")
public class MallMerchant extends BaseEntity {

    /** 商家编号（S+8位） */
    private String merchantNo;

    /** 店铺名（C 端展示） */
    private String name;

    /** 营业执照主体名称 */
    private String legalName;

    /** 营业执照统一社会信用代码 */
    private String licenseNo;

    /** 营业执照图片 URL */
    private String licenseImg;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 经营类目 */
    private String category;

    /** 店铺 LOGO */
    private String logo;

    /** 店铺简介 */
    private String intro;

    /** 平台抽成费率（‰）：50 = 5% */
    private Integer settleRate;

    /** 状态：0待审1启用2停用3驳回 */
    private Integer status;

    /** 驳回原因 */
    private String rejectReason;

    /** 可提现余额（分） */
    private Long accountBalance;

    /** 累计已结算（分） */
    private Long settledTotal;
}
