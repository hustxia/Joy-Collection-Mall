package com.mall.common.feign.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单流转日志精简视图（订单域 → 售后域 内部传输）
 */
@Data
public class OrderLogBrief implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String content;
    private String operator;
    /** 1 会员 / 2 管理员 / 3 系统 */
    private Integer operatorType;
    private LocalDateTime createTime;
}
