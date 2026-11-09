package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallRefund;

import java.util.List;

/**
 * 售后退款单 Service
 */
public interface MallRefundService extends IService<MallRefund> {

    // <gen:begin key="service-body">
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====

    /**
     * 售后状态机唯一实现（V4 P0-1）。
     * 迁移：1→2/3（审核）、2→4（退款，type=2 回补库存）、1/2→5（用户撤销）。
     * 全部条件 UPDATE（WHERE status=前置），受影响行 0 → 400，并发冲突用户可见。
     */

    /** 申请资格校验（无副作用）：订单归属本人、状态 30/40、无进行中售后。 */
    boolean canApply(Long memberId, Long orderId);

    /**
     * 申请售后：锁订单行防并发重复申请，金额=订单实付（服务端计算，不接受前端传值）。
     *
     * @param memberNickname 申请人昵称（写订单日志用）
     */
    MallRefund apply(Long memberId, String memberNickname, Long orderId, Integer type,
                     String reason, List<String> evidenceImages);

    /** 后台审核：仅 status=1。agree=true → 2 已同意；false → 3 已拒绝。 */
    void audit(Long id, boolean agree, String auditRemark, String operator);
    /**
     * 后台执行 mock 退款：仅 status=2 → 4，写 refund_time；type=2 退货退款回补
     * SKU stock 并回减 sales（仅退款不回补）。与状态迁移同事务，条件 UPDATE 保证
     * 重复请求不会二次回补。
     */
    MallRefund doRefund(Long id, String operator);

    /** 用户撤销售后：仅 status in (1,2) → 5 已关闭。 */
    void close(Long refundId, Long memberId, String memberNickname);

    /** 订单域内部用：批量取每个订单的最新一条售后单（一单多条历史只留最新） */
    List<MallRefund> listLatestByOrderIds(List<Long> orderIds);

    /** 订单域内部用：单订单最新售后单，无则 null */
    MallRefund latestByOrderId(Long orderId);

    /**
     * 自动退款（V12）：订单已关闭后收到渠道支付成功回调时落一条终态退款单。
     *
     * <p>不回补库存——关单时已回补过，重复回补会造成库存虚增。
     * 幂等：同订单已存在 status=4 的退款单时直接返回该单，不重复插入。
     */
    MallRefund autoRefund(com.mall.common.feign.dto.RefundAutoRequest request);

    // ===== V15 售后扩展（REQ-P1-06）=====

    /**
     * 申请换货（type=3）：指定订单项 + 换入 SKU + 数量。
     * 状态机：1 待审核 → 2 已同意（待买家寄回）→ 6 买家已寄回 → 7 商家已收货 → 8 已换货（终态）。
     * 库存不立即回补：收货确认时回补换出 SKU，发出换货时扣减换入 SKU（同 SKU 净变化为 0）。
     */
    MallRefund applyExchange(Long memberId, String memberNickname, Long orderId, Long orderItemId,
                             Long exchangeSkuId, Integer quantity, String reason, List<String> evidenceImages);

    /**
     * 申请部分退款（type=4）：按订单项指定金额，一单可多次。
     * 累计（含进行中）不得超过订单实付，超了返回业务 400；金额服务端校验，不接受前端口径。
     */
    MallRefund applyPartial(Long memberId, String memberNickname, Long orderId, Long orderItemId,
                            Long amount, String reason, List<String> evidenceImages);

    /** 买家寄回（换货）：仅 status=2 → 6 买家已寄回，记 ship_time。 */
    void shipBack(Long refundId, Long memberId, String memberNickname);

    /** 商家收货确认（换货）：仅 status=6 → 7，回补换出 SKU 库存（条件 UPDATE 幂等闸门）。 */
    void confirmReceipt(Long id, String operator);

    /** 发出换货（换货）：仅 status=7 → 8（终态），扣减换入 SKU 库存。 */
    void sendExchange(Long id, String operator);

    /**
     * 买家申请平台介入：仅本人 + status=1（待审核）+ 申请时间已超 48 小时 + 未介入过。
     * 条件 UPDATE 置 platform_status=1（仲裁中）。
     */
    void applyIntervene(Long refundId, Long memberId, String memberNickname);

    /**
     * 平台仲裁队列（惰性判定，无定时任务）：status=1（商家待审核未处理）
     * 且 create_time 已超 48 小时，含买家已申请介入（platform_status in (0,1)）的工单。
     */
    Page<MallRefund> arbitrationPage(long pageNum, long pageSize);

    /**
     * 仲裁裁决：forceRefund=true 强制退款（1→2 复用退款主流程到 4 终态，platform_status=2）；
     * false 驳回（platform_status=3，工单回到商家待审核）。
     */
    MallRefund arbitrate(Long id, boolean forceRefund, String remark, String operator);
}