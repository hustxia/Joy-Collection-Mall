package com.mall.dto;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 佣金规则分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallCommissionRulePageDTO extends PageQuery {

    /** 作用域过滤：0全局1指定商品 */
    private Integer scope;
}
