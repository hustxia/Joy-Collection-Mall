package com.mall.ai.dto;

import lombok.Data;

@Data
public class FaqQueryDTO {
    private String keyword;
    private String category;
    private Integer status;
    private long pageNum = 1;
    private long pageSize = 20;
}
