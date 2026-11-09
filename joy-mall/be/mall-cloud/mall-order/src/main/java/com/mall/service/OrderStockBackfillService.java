package com.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.entity.MallOrderItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单库存回补（SKU 级）：取消订单 / 超时关单 / 后台关闭订单共用，避免两处维护库存口径。
 *
 * <p>库存只存在于商品域 {@code mall_sku}，本服务只读自域 {@code mall_order_item} 汇总每个
 * SKU 的购买数量，再经 {@link GoodsInnerClient#restoreStock} 回补到商品域。幂等口径保持不变：
 * 调用方以「订单状态条件 UPDATE」为闸门，同一订单只会回补一次。
 */
@Service
public class OrderStockBackfillService {

    private final MallOrderItemService mallOrderItemService;
    private final GoodsInnerClient goodsInnerClient;

    public OrderStockBackfillService(MallOrderItemService mallOrderItemService,
                                     GoodsInnerClient goodsInnerClient) {
        this.mallOrderItemService = mallOrderItemService;
        this.goodsInnerClient = goodsInnerClient;
    }

    /**
     * 按订单 ID 列表回补 SKU 库存（汇总其下未删除明细的购买数量）。
     *
     * @param orderIds 订单 ID（空列表直接返回，无副作用）
     */
    public void backfill(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<MallOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MallOrderItem::getOrderId, orderIds);
        Map<Long, Integer> skuQty = new LinkedHashMap<>();
        for (MallOrderItem item : mallOrderItemService.list(wrapper)) {
            skuQty.merge(item.getSkuId(), item.getQuantity(), Integer::sum);
        }
        if (!skuQty.isEmpty()) {
            goodsInnerClient.restoreStock(skuQty);
        }
    }
}
