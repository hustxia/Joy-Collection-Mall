package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运费试算结果（商品域 → 订单域，V12）。
 *
 * <p>{@code reason} 只用于排查与日志（例如「未匹配到模板，按包邮处理」），不直接展示给用户；
 * 用户可见文案统一走 {@code tip}。
 */
@Data
public class FreightQuoteResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 运费（分），恒 >= 0 */
    private Long amount = 0L;

    /** C 端提示文案：满额包邮差额 / 已包邮 / 空串 */
    private String tip;

    /** 距包邮门槛还差多少分（0 表示已包邮或无门槛） */
    private Long freeGap = 0L;

    /** 命中的模板ID（无模板为 null） */
    private Long templateId;

    /** 命中的模板名 */
    private String templateName;

    /** 是否命中可用模板（false 时 amount=0，调用方打 WARN） */
    private Boolean matched = Boolean.TRUE;

    /** 未命中原因（排查用） */
    private String reason;
}
