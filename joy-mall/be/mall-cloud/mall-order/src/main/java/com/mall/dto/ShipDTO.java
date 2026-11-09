package com.mall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 发货入参（V12）：items 为空表示「把剩余未发数量全部发出」。
 */
@Data
public class ShipDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long orderId;

    private String expressCompany;

    private String expressNo;

    private String remark;

    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private Long orderItemId;
        private Integer quantity;
    }
}
