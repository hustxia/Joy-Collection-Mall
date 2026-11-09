package com.mall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.entity.MallGoods;

/**
 * 商品 Service
 */
public interface MallGoodsService extends IService<MallGoods> {

    // <gen:begin key="service-body">
    // <gen:end key="service-body">
    // ===== 以下为手写区：自定义方法写在这里，重新生成不会被覆盖 =====

    /** 累加商品销量：{goodsId: 数量}（支付成功用） */
    void incrementSales(java.util.Map<Long, Integer> deltas);
}