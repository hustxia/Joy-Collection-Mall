package com.mall.dto;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 结算单分页查询入参
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallMerchantSettlementPageDTO extends PageQuery {

    private Long merchantId;

    private Integer status;
}
