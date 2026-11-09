package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 消息发送日志（V14，REQ-P1-09）：一次发送一行，记录最终结果、重试次数与是否降级。
 *
 * <p>降级判定唯一依据是 {@code degraded=1}：验收要求「短信通道故障时站内信仍写入且日志标记降级」，
 * 所以降级成功也不能记成 status=1（成功），必须记 status=2（降级成功）+ degraded=1。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_message_log")
public class MallMessageLog extends BaseEntity {

    /** 状态：失败 */
    public static final int STATUS_FAILED = 0;
    /** 状态：主通道成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 状态：降级通道成功 */
    public static final int STATUS_DEGRADED = 2;

    /** 场景码 */
    private String sceneCode;

    /** 接收人（会员ID） */
    private Long memberId;

    /** 最终通道：sms/site（全失败记最后尝试的通道） */
    private String channel;

    /** 渲染后标题 */
    private String title;

    /** 渲染后内容 */
    private String content;

    /** 状态：0失败1成功2降级成功 */
    private Integer status;

    /** 主通道失败重试次数（不含首次） */
    private Integer retryCount;

    /** 降级标记：1发生了降级 */
    private Integer degraded;

    /** 最后一次失败原因 */
    private String errorMsg;

    /** 发送完成时间 */
    private LocalDateTime sendTime;

    /** 业务单号（订单号/售后单号） */
    private String bizId;
}
