package com.mall.dto;

import lombok.Data;

/**
 * 抽奖活动分页查询 DTO
 */
@Data
public class MallLotteryPageDTO {

    private long pageNum = 1;

    private long pageSize = 10;

    /** 状态：0未开始1进行中2已结束（可选） */
    private Integer status;

    /** 活动名称模糊（可选） */
    private String name;
}
