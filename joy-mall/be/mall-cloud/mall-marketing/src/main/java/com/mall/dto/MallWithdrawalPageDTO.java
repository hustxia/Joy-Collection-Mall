package com.mall.dto;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提现单分页查询 DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallWithdrawalPageDTO extends PageQuery {

    /** 状态过滤：0申请1审核中2已打款3拒绝 */
    private Integer status;
}
