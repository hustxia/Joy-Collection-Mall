package com.mall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量发货入参（V12）：一行一个订单，逐行校验，失败行不影响其他行。
 */
@Data
public class DeliveryBatchDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<Row> rows = new ArrayList<>();

    @Data
    public static class Row implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String orderNo;
        private String expressCompany;
        private String expressNo;
    }
}
