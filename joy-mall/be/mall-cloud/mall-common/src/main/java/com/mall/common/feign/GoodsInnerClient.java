package com.mall.common.feign;

import com.mall.common.feign.dto.FreightQuoteRequest;
import com.mall.common.feign.dto.FreightQuoteResult;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.feign.dto.SkuBrief;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 商品域内部客户端：订单域创建/取消/退款时读取 SKU/商品、扣减与回补库存。
 * <p>静态地址（无注册中心）：url 取 feign.clients.mall-goods，缺省 http://localhost:8202。
 */
@FeignClient(name = "mall-goods", url = "${feign.clients.mall-goods:http://localhost:8202}",
        fallback = com.mall.common.feign.fallback.GoodsInnerClientFallback.class)
public interface GoodsInnerClient {

    @GetMapping("/inner/sku/by-ids")
    List<SkuBrief> skuByIds(@RequestParam("ids") List<Long> ids);

    @GetMapping("/inner/goods/by-ids")
    List<GoodsBrief> goodsByIds(@RequestParam("ids") List<Long> ids);

    /** 按商品查 SKU 列表（V8 抽奖实物奖品建单时解析默认 SKU 用） */
    @GetMapping("/inner/sku/by-goods")
    List<SkuBrief> skuByGoods(@RequestParam("goodsId") Long goodsId);

    /** 扣减库存：{skuId: 数量}；任一 SKU 库存不足返回 false（下单防超卖用） */
    @PostMapping("/inner/sku/deduct")
    Boolean deductStock(@RequestBody Map<Long, Integer> deltas);

    /** 回补库存：{skuId: 数量}（取消/关闭订单用） */
    @PostMapping("/inner/sku/restore")
    void restoreStock(@RequestBody Map<Long, Integer> deltas);

    /** 累加 SKU 销量：{skuId: 数量}（支付成功用） */
    @PostMapping("/inner/sku/sales")
    void incrementSkuSales(@RequestBody Map<Long, Integer> deltas);

    /** 累加商品销量：{goodsId: 数量}（支付成功用） */
    @PostMapping("/inner/goods/sales")
    void incrementGoodsSales(@RequestBody Map<Long, Integer> deltas);

    /**
     * 运费试算（V12）：按店铺一次，回填 amount/tip/freeGap。
     * <p>订单域不感知计费规则与商品重量，只做「各店铺运费求和」。
     */
    @PostMapping("/inner/freight/quote")
    FreightQuoteResult freightQuote(@RequestBody FreightQuoteRequest request);
}
