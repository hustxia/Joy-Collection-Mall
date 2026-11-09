package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallSku;

/**
 * 商品SKU Service
 */
public interface MallSkuService extends IService<MallSku> {

    // <gen:begin key="service-body">
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====

    /** 条件扣减库存：{skuId: 数量}，任一不足则回补已扣部分并返回 false（下单防超卖） */
    boolean deductStock(java.util.Map<Long, Integer> deltas);

    /** 回补库存：{skuId: 数量}（取消/关闭订单用） */
    void restoreStock(java.util.Map<Long, Integer> deltas);

    /** 累加销量：{skuId: 数量}（支付成功用） */
    void incrementSales(java.util.Map<Long, Integer> deltas);
}