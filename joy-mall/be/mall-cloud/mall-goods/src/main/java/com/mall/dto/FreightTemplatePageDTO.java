package com.mall.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运费模板分页入参（V12）
 */
@Data
public class FreightTemplatePageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 模板名模糊 */
    private String keyword;

    /** 状态：1启用0停用，空=全部 */
    private Integer status;

    private long pageNum = 1;

    private long pageSize = 10;
}
