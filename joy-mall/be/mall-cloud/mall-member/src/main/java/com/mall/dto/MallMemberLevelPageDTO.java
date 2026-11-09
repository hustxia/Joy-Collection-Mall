package com.mall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员等级分页入参（V13）
 */
@Data
public class MallMemberLevelPageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String keyword;

    private Integer status;

    private long pageNum = 1;

    private long pageSize = 20;
}
