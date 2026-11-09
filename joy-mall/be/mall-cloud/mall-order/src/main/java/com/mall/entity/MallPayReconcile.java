package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 支付对账差异（V12）：每日渠道账单与本地 {@code mall_payment} 逐笔比对，只落差异行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_pay_reconcile")
public class MallPayReconcile extends BaseEntity {

    /** 差异：本地金额大于渠道（多记） */
    public static final int DIFF_LONG = 1;
    /** 差异：本地金额小于渠道（少记，通常是掉单） */
    public static final int DIFF_SHORT = 2;
    /** 本地有单、渠道账单缺失 */
    public static final int DIFF_LOCAL_ONLY = 3;
    /** 渠道有单、本地缺失 */
    public static final int DIFF_CHANNEL_ONLY = 4;

    public static final int HANDLE_WAIT = 0;
    public static final int HANDLE_CONFIRMED = 1;
    public static final int HANDLE_IGNORED = 2;

    private LocalDate billDate;

    private String channel;

    private String payNo;

    private String channelTradeNo;

    private Long channelAmount;

    private Long localAmount;

    private Integer diffType;

    private Integer handleStatus;

    private String handleRemark;

    private String handleBy;

    private LocalDateTime handleTime;

    public static String diffText(Integer diffType) {
        if (diffType == null) {
            return "-";
        }
        return switch (diffType) {
            case DIFF_LONG -> "本地多记（长款）";
            case DIFF_SHORT -> "本地少记（短款）";
            case DIFF_LOCAL_ONLY -> "渠道账单缺失";
            case DIFF_CHANNEL_ONLY -> "本地掉单";
            default -> String.valueOf(diffType);
        };
    }
}
