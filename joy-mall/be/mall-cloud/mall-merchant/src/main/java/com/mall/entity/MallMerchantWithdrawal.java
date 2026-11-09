package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商家提现单：申请即冻结余额（先扣 account_balance），审核拒绝时原路退回。
 *
 * <p>状态：0 申请 / 1 审核中 / 2 已打款 / 3 拒绝。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_merchant_withdrawal")
public class MallMerchantWithdrawal extends BaseEntity {

    private Long merchantId;

    /** 提现金额（分） */
    private Long amount;

    /** 提现方式：0余额1微信2银行卡 */
    private Integer method;

    private Integer status;

    private String auditBy;

    private LocalDateTime auditTime;

    private String rejectReason;
}
