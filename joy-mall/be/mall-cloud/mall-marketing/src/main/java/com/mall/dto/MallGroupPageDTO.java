package com.mall.dto;

import lombok.Data;

/**
 * 拼团活动分页查询 DTO
 */
@Data
public class MallGroupPageDTO {

    private long pageNum = 1;

    private long pageSize = 10;

    /** 状态：0未开始1进行中2已结束（可选） */
    private Integer status;

    /** 商品ID（可选） */
    private Long goodsId;
}
