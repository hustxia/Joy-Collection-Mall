package com.mall.dto;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分销员分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallDistributorPageDTO extends PageQuery {

    /** 状态过滤：0待审1启用2停用3驳回 */
    private Integer status;
}
