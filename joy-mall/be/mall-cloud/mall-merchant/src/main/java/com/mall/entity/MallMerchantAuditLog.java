package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商家审核流水：提交 / 通过 / 驳回 / 停用 / 启用 全留痕，用于后台详情时间线。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_merchant_audit_log")
public class MallMerchantAuditLog extends BaseEntity {

    /** 动作：0提交1通过2驳回3停用4启用 */
    public static final int ACTION_APPLY = 0;
    public static final int ACTION_PASS = 1;
    public static final int ACTION_REJECT = 2;
    public static final int ACTION_DISABLE = 3;
    public static final int ACTION_ENABLE = 4;

    private Long merchantId;

    private Integer action;

    private String operator;

    private String remark;

    public static String actionText(Integer action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case ACTION_APPLY -> "提交入驻";
            case ACTION_PASS -> "审核通过";
            case ACTION_REJECT -> "审核驳回";
            case ACTION_DISABLE -> "停用店铺";
            case ACTION_ENABLE -> "启用店铺";
            default -> "未知";
        };
    }
}
