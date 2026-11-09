package com.mall.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板（V14，REQ-P1-09）：一个场景一条模板，变量用 {@code ${var}} 占位。
 *
 * <p>通道策略：{@code channel} 是主通道，失败重试后统一降级站内信（site）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_message_template")
public class MallMessageTemplate extends BaseEntity {

    /** 场景码：支付成功 */
    public static final String SCENE_PAY_SUCCESS = "PAY_SUCCESS";
    /** 场景码：订单发货 */
    public static final String SCENE_ORDER_SHIPPED = "ORDER_SHIPPED";
    /** 场景码：退款到账 */
    public static final String SCENE_REFUND_ARRIVED = "REFUND_ARRIVED";

    /** 场景码 */
    private String sceneCode;

    /** 场景名称 */
    private String sceneName;

    /** 标题模板 */
    private String titleTemplate;

    /** 内容模板 */
    private String contentTemplate;

    /** 主通道：sms/site */
    private String channel;

    /** 状态：1启用0停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
