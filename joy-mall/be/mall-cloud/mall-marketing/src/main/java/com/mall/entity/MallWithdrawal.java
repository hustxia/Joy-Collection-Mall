package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 分销员提现
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_withdrawal")
public class MallWithdrawal extends BaseEntity {

    /** 分销员ID */
    private Long distributorId;

    /** 提现金额（分） */
    private Long amount;

    /** 提现方式：0余额1微信2银行卡 */
    private Integer method;

    /** 状态：0申请1审核中2已打款3拒绝 */
    private Integer status;

    /** 审核人 */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 审核备注 */
    private String auditRemark;
}
