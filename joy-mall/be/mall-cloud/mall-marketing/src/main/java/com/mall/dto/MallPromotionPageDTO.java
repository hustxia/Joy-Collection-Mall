package com.mall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 促销活动分页入参（V13）
 */
@Data
public class MallPromotionPageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 活动名模糊 */
    private String keyword;

    /** 类型：1~6，空=全部 */
    private Integer type;

    /** 状态：1上线0下线，空=全部 */
    private Integer status;

    private long pageNum = 1;

    private long pageSize = 10;
}
