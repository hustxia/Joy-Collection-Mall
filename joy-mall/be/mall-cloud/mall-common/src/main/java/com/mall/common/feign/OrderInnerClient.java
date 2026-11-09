package com.mall.common.feign;

import com.mall.common.feign.dto.OrderBrief;
import com.mall.common.feign.dto.OrderItemBrief;
import com.mall.common.feign.dto.OrderLogAppendDTO;
import com.mall.common.feign.dto.OrderLogBrief;
import com.mall.common.feign.dto.PromoOrderCreateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 订单域内部客户端：售后域申请/审核/退款时读取订单与明细、读取并追加订单流转日志。
 * <p>静态地址：url 取 feign.clients.mall-order，缺省 http://localhost:8204。
 */
@FeignClient(name = "mall-order", url = "${feign.clients.mall-order:http://localhost:8204}")
public interface OrderInnerClient {

    @GetMapping("/inner/order/{id}")
    OrderBrief order(@PathVariable("id") Long id);

    @GetMapping("/inner/order-item")
    List<OrderItemBrief> orderItems(@RequestParam("orderId") Long orderId);

    @GetMapping("/inner/order-log")
    List<OrderLogBrief> orderLogs(@RequestParam("orderId") Long orderId);

    /** 售后域追加订单流转日志（订单域持久化到 mall_order_log） */
    @PostMapping("/inner/order-log")
    void appendLog(@RequestBody OrderLogAppendDTO dto);

    /** V7 商品评价：订单项是否可被该会员评价（true=可评；订单域判定归属/完成态/未评过） */
    @GetMapping("/inner/order-item/can-review")
    Boolean canReview(@RequestParam("orderItemId") Long orderItemId, @RequestParam("memberId") Long memberId);

    /** V8 营销玩法建单（秒杀/拼团/抽奖实物）：返回订单摘要（id/orderNo/payAmount），失败抛业务异常 */
    @PostMapping("/inner/order/create-promo")
    OrderBrief createPromoOrder(@RequestBody PromoOrderCreateDTO dto);

    /**
     * V12 发票红冲：售后域退款完成后调用，已开票的发票置已红冲。
     * @return true=本次红冲成功；false=该订单没有已开票发票（无需红冲）
     */
    @PostMapping("/inner/invoice/red-flush")
    Boolean redFlushInvoice(@RequestParam("orderId") Long orderId, @RequestParam("remark") String remark);

    /** V16 AI 客服：会员最近一笔订单（未指定订单号时取证用） */
    @GetMapping("/inner/order/latest")
    OrderBrief latestOrder(@RequestParam("memberId") Long memberId);

    /** V16 AI 客服：按订单号查询（校验归属会员，不是本人的订单返回 null） */
    @GetMapping("/inner/order/by-no")
    OrderBrief orderByNo(@RequestParam("orderNo") String orderNo, @RequestParam("memberId") Long memberId);

    /** V16 AI 客服：物流轨迹（复用订单域 trackByOrder，同样做归属校验） */
    @GetMapping("/inner/delivery/track")
    Map<String, Object> deliveryTrack(@RequestParam("orderId") Long orderId, @RequestParam("memberId") Long memberId);
}
