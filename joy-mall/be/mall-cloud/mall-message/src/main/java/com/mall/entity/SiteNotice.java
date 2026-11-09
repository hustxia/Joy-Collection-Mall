package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 站内信（V14 复用 V7 的 {@code mall_notice} 表）。
 *
 * <p>单独建一个实体而不是复用 mall-member 的 {@code MallNotice}：消息服务不引入会员域模块依赖，
 * 只认表结构。字段与表一一对应，不多不少。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_notice")
public class SiteNotice extends BaseEntity {

    /** 类型：订单 */
    public static final int TYPE_ORDER = 1;
    /** 类型：售后 */
    public static final int TYPE_REFUND = 2;
    /** 类型：营销 */
    public static final int TYPE_MARKETING = 3;

    /** 会员ID */
    private Long memberId;

    /** 类型：1订单2售后3营销 */
    private Integer type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 关联业务号（订单号/售后单号） */
    private String bizId;

    /** 读取状态：0未读1已读 */
    private Integer readStatus;
}
