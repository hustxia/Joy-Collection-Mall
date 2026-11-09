package com.mall.ai.dto;

import lombok.Data;

@Data
public class ChatQueryDTO {
    /** 0 机器人 1 已转人工 2 已结束 */
    private Integer status;
    private String intent;
    /** 会员 ID 或会话 ID 关键字 */
    private String keyword;
    private long pageNum = 1;
    private long pageSize = 20;
}
