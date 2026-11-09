package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 发票申请（V12）：一个订单只能开一张发票，金额恒等于订单实付（不接受前端传值）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_invoice")
public class MallInvoice extends BaseEntity {

    /** 状态：待开票 */
    public static final int ST_WAIT = 0;
    /** 状态：已开票 */
    public static final int ST_ISSUED = 1;
    /** 状态：已红冲 */
    public static final int ST_RED_FLUSHED = 2;

    public static final int TITLE_PERSONAL = 1;
    public static final int TITLE_COMPANY = 2;

    private String invoiceNo;

    private Long orderId;

    private String orderNo;

    private Long memberId;

    private Long merchantId;

    /** 1 个人 / 2 企业 */
    private Integer titleType;

    private String title;

    /** 纳税人识别号：企业必填，15-20 位字母数字 */
    private String taxNo;

    private String email;

    private Long amount;

    private Integer status;

    private String fileUrl;

    private LocalDateTime issueTime;

    private LocalDateTime redFlushTime;

    private String remark;

    public static String statusText(Integer status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case ST_WAIT -> "待开票";
            case ST_ISSUED -> "已开票";
            case ST_RED_FLUSHED -> "已红冲";
            default -> String.valueOf(status);
        };
    }
}
