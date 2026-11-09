package com.mall.dto;

import lombok.Data;

/**
 * 商家提现申请入参（本期由平台代商家发起）
 */
@Data
public class WithdrawalApplyDTO {

    private Long merchantId;

    /** 提现金额（分） */
    private Long amount;

    /** 提现方式：0余额1微信2银行卡 */
    private Integer method;
}
