package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客服工单（V14，REQ-P1-08）。
 *
 * <p>状态机：1 待处理 →（客服回复）→ 2 已回复 →（任一方关闭）→ 3 已关闭。
 * 会员回复不会把状态回退到 1（保持「已回复 + 有新留言」更贴近客服视角），
 * 但会刷新 {@code lastReplyTime}，后台按它排「有新留言」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_ticket")
public class MallTicket extends BaseEntity {

    /** 分类：商品咨询 */
    public static final int CATEGORY_GOODS = 1;
    /** 分类：订单售后 */
    public static final int CATEGORY_ORDER = 2;
    /** 分类：物流 */
    public static final int CATEGORY_LOGISTICS = 3;
    /** 分类：支付 */
    public static final int CATEGORY_PAYMENT = 4;
    /** 分类：其他 */
    public static final int CATEGORY_OTHER = 5;

    /** 工单号：T + yyyyMMdd + 6 位随机 */
    private String ticketNo;

    /** 提交会员ID */
    private Long memberId;

    /** 关联订单ID（可空） */
    private Long orderId;

    /** 订单号（冗余，便于客服检索） */
    private String orderNo;

    /** 分类：1商品咨询2订单售后3物流4支付5其他 */
    private Integer category;

    /** 标题 */
    private String title;

    /** 问题描述 */
    private String content;

    /** 状态：1待处理2已回复3已关闭 */
    private Integer status;

    /** 优先级：1低2中3高 */
    private Integer priority;

    /** 首次回复时间（首响 SLA 计时终点） */
    private LocalDateTime firstReplyTime;

    /** 最近回复时间 */
    private LocalDateTime lastReplyTime;

    /** 首响超时标记：0正常1超 24h 未首响 */
    private Integer timeoutFlag;

    /** 关闭时间 */
    private LocalDateTime closeTime;

    /** 分类中文名（列表展示用，非数据库列） */
    public static String categoryText(Integer category) {
        if (category == null) {
            return "-";
        }
        return switch (category) {
            case CATEGORY_GOODS -> "商品咨询";
            case CATEGORY_ORDER -> "订单售后";
            case CATEGORY_LOGISTICS -> "物流";
            case CATEGORY_PAYMENT -> "支付";
            default -> "其他";
        };
    }

    /** 状态中文名 */
    public static String statusText(Integer status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case 1 -> "待处理";
            case 2 -> "已回复";
            case 3 -> "已关闭";
            default -> String.valueOf(status);
        };
    }
}
