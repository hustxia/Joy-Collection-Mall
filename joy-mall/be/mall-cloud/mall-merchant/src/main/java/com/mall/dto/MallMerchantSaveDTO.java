package com.mall.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商家资料修改入参（后台编辑店铺信息与抽成费率）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallMerchantSaveDTO extends MallMerchantApplyDTO {

    private Long id;
}
