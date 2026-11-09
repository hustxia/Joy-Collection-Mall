package com.mall.merchant.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 商品域商家内部端点（V11）：商家后台的商品读写全部经此转发，商品域只按 merchantId 过滤，
 * 不认商家令牌——两侧职责清楚，商家域负责鉴权与归属，商品域负责落库。
 *
 * <p>静态地址：url 取 feign.clients.mall-goods，缺省 http://localhost:8202。
 */
@FeignClient(name = "mall-goods-merchant", url = "${feign.clients.mall-goods:http://localhost:8202}")
public interface MerchantGoodsClient {

    @GetMapping("/inner/merchant/goods/page")
    Map<String, Object> page(@RequestParam("merchantId") Long merchantId,
                             @RequestParam(value = "keyword", required = false) String keyword,
                             @RequestParam(value = "status", required = false) Integer status,
                             @RequestParam("pageNum") long pageNum,
                             @RequestParam("pageSize") long pageSize);

    @GetMapping("/inner/merchant/goods/{id}")
    Map<String, Object> detail(@PathVariable("id") Long id, @RequestParam("merchantId") Long merchantId);

    @PostMapping("/inner/merchant/goods")
    Map<String, Object> create(@RequestBody Map<String, Object> body);

    @PutMapping("/inner/merchant/goods")
    Map<String, Object> update(@RequestBody Map<String, Object> body);

    @PutMapping("/inner/merchant/goods/status")
    Map<String, Object> changeStatus(@RequestParam("merchantId") Long merchantId,
                                     @RequestParam("id") Long id,
                                     @RequestParam("status") Integer status);

    @PutMapping("/inner/merchant/sku/stock")
    Map<String, Object> updateStock(@RequestParam("merchantId") Long merchantId,
                                    @RequestParam("skuId") Long skuId,
                                    @RequestParam("stock") Integer stock);
}
