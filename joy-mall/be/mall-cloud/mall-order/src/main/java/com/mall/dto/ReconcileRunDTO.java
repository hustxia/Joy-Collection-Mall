package com.mall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 支付对账执行入参（V12）。
 *
 * <p>{@code bills} 为空时按「沙箱镜像」对账：直接把本地成功支付单当渠道账单（差异恒 0），
 * 用于日常巡检；接真实渠道后由下载账单任务填这个列表。
 */
@Data
public class ReconcileRunDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDate billDate;

    private String channel = "sandbox";

    private List<Bill> bills = new ArrayList<>();

    @Data
    public static class Bill implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String channelTradeNo;
        private Long amount;
    }
}
