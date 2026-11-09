package com.mall.dto;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商家分页查询入参（后台）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallMerchantPageDTO extends PageQuery {

    /** 店铺名/商家编号/联系人 模糊匹配 */
    private String keyword;

    /** 状态：0待审1启用2停用3驳回 */
    private Integer status;

    private String category;
}
