package com.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商家入驻申请入参：平台代提交（本期无独立商家后台登录）。
 */
@Data
public class MallMerchantApplyDTO {

    @Schema(description = "店铺名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    private String legalName;

    /** 统一社会信用代码，同一执照在未驳回前只能提交一次 */
    private String licenseNo;

    private String licenseImg;

    private String contactName;

    private String contactPhone;

    private String category;

    private String logo;

    private String intro;

    /** 平台抽成费率（‰）；为空时取全局默认（sys_dict_data: mall_settle_rate） */
    private Integer settleRate;
}
