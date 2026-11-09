package com.mall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.dto.ShopConfirmDTO;
import com.mall.dto.ShopOrderIdDTO;
import com.mall.dto.ShopSubmitDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * C 端订单业务（V3）：确认预览 / 提交 / 我的订单 / 详情 / 取消 / 确认收货。
 *
 * <p>状态机口径：10 待付款→20 待发货→30 已发货→40 已完成；10→50 取消。
 * 所有迁移走条件 UPDATE（WHERE status=前置态），受影响 0 行即并发/非法，天然幂等。
 * 库存下单即扣（防超卖条件更新），取消回补；金额单位分。
 *
 * <p>微服务边界：订单/明细/日志自持（mall_order*）；地址/购物车/会员经会员域、
 * SKU/商品与库存经商品域、优惠券经营销域、售后经售后域的 <b>Feign</b> 内部接口访问，
 * 不再跨库直连他域表。
 */
public interface ShopOrderService {

    /** 待付款 */
    int STATUS_UNPAID = 10;
    /** 待发货 */
    int STATUS_PAID = 20;
    /** 已发货 */
    int STATUS_DELIVERED = 30;
    /** 已完成 */
    int STATUS_FINISHED = 40;
    /** 已取消 */
    int STATUS_CANCELLED = 50;
    /** V10：平台自营店铺（历史数据与自营商品默认归属） */
    long PLATFORM_MERCHANT_ID = 1L;

    /** 确认页预览（不落库）：重算价格/库存/运费 + 可用券 + 按店铺分组 */
    Map<String, Object> confirm(Long memberId, ShopConfirmDTO dto);

    /** 提交订单：按商家拆单→校验→条件扣库存→写单+明细+log→清购物车（事务） */
    Map<String, Object> submit(Long memberId, ShopSubmitDTO dto);

    /** 我的订单分页（status 可选：10/20/30/40），带售后角标 */
    Page<Map<String, Object>> page(Long memberId, Integer status, long pageNum, long pageSize);

    /** 订单详情：订单+明细+状态时间线+最新售后单 */
    Map<String, Object> detail(Long memberId, Long id);

    /** 取消订单：仅待付款，回补库存+解锁券 */
    void cancel(Long memberId, Long orderId);

    /** 确认收货：仅已发货 → 已完成，触发商家结算与完成通知 */
    void confirmReceipt(Long memberId, Long orderId);

    /** 订单号：M + yyyyMMddHHmmss + 6 位随机（shop 下单与 inner 补单共用） */
    static String newOrderNo() {
        return "M" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }
}
