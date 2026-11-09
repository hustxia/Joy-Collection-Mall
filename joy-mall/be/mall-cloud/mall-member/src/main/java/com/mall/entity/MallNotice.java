package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内消息（V7 手写）：订单/售后/营销通知，按会员隔离，已读走条件更新。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_notice")
public class MallNotice extends BaseEntity {

    /**
     * 会员ID 关联mall_member
     */
    private Long memberId;

    /**
     * 类型:1订单2售后3营销
     */
    private Integer type;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 关联业务号（订单号/售后单号）
     */
    private String bizId;

    /**
     * 读取状态:0未读1已读
     */
    private Integer readStatus;
}
