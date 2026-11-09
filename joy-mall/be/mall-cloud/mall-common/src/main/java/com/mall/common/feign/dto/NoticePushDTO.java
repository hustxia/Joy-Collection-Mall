package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 站内消息推送请求（订单/售后域 → 会员域 内部传输，V7）
 */
@Data
public class NoticePushDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long memberId;
    /** 1订单 2售后 3营销 */
    private Integer type;
    private String title;
    private String content;
    /** 关联业务号（订单号/售后单号），便于前端跳转 */
    private String bizId;
}
