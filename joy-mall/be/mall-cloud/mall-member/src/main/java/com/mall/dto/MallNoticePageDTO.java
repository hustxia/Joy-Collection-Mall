package com.mall.dto;

import com.mall.common.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内消息分页查询 DTO（V7 手写）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MallNoticePageDTO extends PageQuery {

    /**
     * 会员ID 关联mall_member
     */
    private Long memberId;

    /**
     * 类型:1订单2售后3营销
     */
    private Integer type;

    /**
     * 读取状态:0未读1已读
     */
    private Integer readStatus;
}
