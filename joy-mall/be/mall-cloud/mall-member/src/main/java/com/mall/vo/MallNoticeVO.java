package com.mall.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内消息 VO（V7 手写）
 */
@Data
public class MallNoticeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long memberId;
    private Integer type;
    private String title;
    private String content;
    private String bizId;
    private Integer readStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
