package com.mall.message;

/**
 * 一次消息发送请求（V14）：标题/内容已经是渲染后的终态，通道不再做模板处理。
 *
 * @param sceneCode 场景码（PAY_SUCCESS / ORDER_SHIPPED / REFUND_ARRIVED）
 * @param memberId  接收会员
 * @param title     渲染后标题
 * @param content   渲染后内容
 * @param bizId     业务单号（订单号/售后单号），用于站内信跳转与对账
 */
public record MessageRequest(String sceneCode, Long memberId, String title, String content, String bizId) {
}
