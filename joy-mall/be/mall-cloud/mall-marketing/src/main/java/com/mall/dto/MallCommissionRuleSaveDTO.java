package com.mall.dto;

import lombok.Data;

/**
 * 佣金规则保存 DTO
 */
@Data
public class MallCommissionRuleSaveDTO {

    /** 更新时传 */
    private Long id;

    /** 作用域：0全局1指定商品 */
    private Integer scope;

    /** 指定商品ID（scope=1时必填） */
    private Long goodsId;

    /** 佣金比例（‰） */
    private Integer rate;

    /** 固定佣金（分），与rate二选一优先fixed */
    private Long fixed;

    /** 分销层级：默认1（仅一级分销） */
    private Integer level;

    /** 状态：1启用0停用 */
    private Integer status;
}
