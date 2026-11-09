package com.mall.message;

/**
 * 消息通道 SPI（V14，REQ-P1-09）。
 *
 * <p>新增通道（微信模板消息、钉钉、邮件）只需实现本接口并注册为 Spring Bean，
 * 调度器不感知具体通道；降级目标固定为 {@link #CODE_SITE}。
 */
public interface MessageChannel {

    /** 站内信通道码：降级目标 */
    String CODE_SITE = "site";
    /** 短信通道码 */
    String CODE_SMS = "sms";

    /** 通道码：sms / site / 未来扩展 */
    String code();

    /** 是否可用：未配置（缺服务商密钥/未开通）必须返回 false，让调度器直接降级而不是无谓重试 */
    boolean available();

    /** 发送；实现必须自己吞掉底层异常，用 {@link SendResult} 表达失败原因 */
    SendResult send(MessageRequest request);

    /**
     * 发送结果。
     *
     * @param success 是否成功
     * @param error   失败原因（成功时为 null）
     */
    record SendResult(boolean success, String error) {

        public static SendResult ok() {
            return new SendResult(true, null);
        }

        public static SendResult fail(String error) {
            return new SendResult(false, error);
        }
    }
}
