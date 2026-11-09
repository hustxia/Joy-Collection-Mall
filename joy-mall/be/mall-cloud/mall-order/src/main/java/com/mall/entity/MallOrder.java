package com.mall.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_order")
public class MallOrder extends BaseEntity {

    // <gen:begin key="entity-fields">
    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 会员ID 关联mall_member
     */
    private Long memberId;

    /**
     * 商品总额(分)
     */
    private Long totalAmount;

    /**
     * 运费(分)
     */
    private Long freightAmount;

    /**
     * 优惠金额(分)
     */
    private Long discountAmount;

    /**
     * 实付金额(分)
     */
    private Long payAmount;

    /**
     * 订单状态:10待付款20待发货30已发货40已完成50已取消60已关闭
     */
    private Integer status;

    /**
     * 支付状态:1已支付0未支付
     */
    private Integer payStatus;

    /**
     * 支付方式:1微信2支付宝
     */
    private Integer payType;

    /**
     * 支付单号 关联pay_order(V6.3)
     */
    private String payOrderNo;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 收货人
     */
    private String receiverName;

    /**
     * 联系电话
     */
    private String receiverPhone;

    /**
     * 收货地址(下单快照)
     */
    private String receiverAddress;

    /**
     * 快递公司
     */
    private String expressCompany;

    /**
     * 快递单号
     */
    private String expressNo;

    /**
     * 发货时间
     */
    private LocalDateTime deliverTime;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;

    /**
     * 买家留言
     */
    private String buyerRemark;

    /**
     * 管理员备注
     */
    private String remark;

    // <gen:end key="entity-fields">
    // ===== 以下为手写区：写在 gen:end 之后的内容，重新生成不会被覆盖 =====

    /**
     * 关闭时间（超时关单 / 后台手动关闭）。V4 收尾新增列，见 db/v4-tail.sql。
     */
    private LocalDateTime closeTime;

    /**
     * 订单类型：0普通 1秒杀 2拼团 3抽奖实物。V8 新增列，见 db/v8-promo.sql。
     */
    private Integer orderType;

    /**
     * 商家归属：1=平台自营店铺。V10 新增列，见 db/v10-merchant.sql。
     * 购物车按商家拆单，一个订单只属于一个商家。
     */
    private Long merchantId;
}