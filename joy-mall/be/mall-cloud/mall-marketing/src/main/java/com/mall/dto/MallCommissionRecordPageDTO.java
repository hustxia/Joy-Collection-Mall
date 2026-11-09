package com.mall.dto;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 佣金记录分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallCommissionRecordPageDTO extends PageQuery {

    /** 分销员ID过滤 */
    private Long distributorId;

    /** 状态过滤：0待结算1已结算2失效 */
    private Integer status;
}
