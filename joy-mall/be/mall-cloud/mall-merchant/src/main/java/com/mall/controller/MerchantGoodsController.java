package com.mall.controller;

import com.mall.common.context.MerchantContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.merchant.feign.MerchantGoodsClient;
import com.mall.merchant.support.MerchantGuard;
import com.mall.merchant.support.MerchantResults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商家商品自助（V11）：列表 / 详情 / 新增 / 修改 / 上下架 / 库存。
 *
 * <p>数据落在商品域，这里只做三件事：取当前商家、校验归属（越权 403 + 留痕）、转发。
 * {@code merchantId} 一律取自令牌上下文，请求体里带了也不采信。
 */
@Tag(name = "商家后台-商品", description = "商家商品与库存自助维护")
@RestController
@RequestMapping("/merchant-admin")
public class MerchantGoodsController {

    private final MerchantGoodsClient goodsClient;
    private final MerchantGuard guard;

    public MerchantGoodsController(MerchantGoodsClient goodsClient, MerchantGuard guard) {
        this.goodsClient = goodsClient;
        this.guard = guard;
    }

    @Operation(summary = "本店商品分页")
    @GetMapping("/goods/page")
    public Result<Map<String, Object>> page(@RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        Long merchantId = guard.currentMerchantId();
        return Result.ok(MerchantResults.unwrap(
                goodsClient.page(merchantId, keyword, status, pageNum, pageSize)));
    }

    @Operation(summary = "商品详情（含 SKU）；非本店商品 403")
    @GetMapping("/goods/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        guard.currentMerchantId();
        if (!guard.assertOwned("mall_goods", id, "查看商品")) {
            throw new BusinessException(400, "商品不存在");
        }
        return Result.ok(MerchantResults.unwrap(
                goodsClient.detail(id, MerchantContext.get())));
    }

    @Operation(summary = "新增商品（含 SKU）")
    @Log(title = "商家商品", type = "INSERT")
    @PostMapping("/goods")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long merchantId = guard.currentMerchantId();
        return Result.ok(MerchantResults.unwrap(goodsClient.create(payload(merchantId, body))));
    }

    @Operation(summary = "修改商品（含 SKU 全量覆盖）；非本店商品 403")
    @Log(title = "商家商品", type = "UPDATE")
    @PutMapping("/goods")
    public Result<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        Long merchantId = guard.currentMerchantId();
        Long id = asLong(body.get("id"));
        if (id == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        if (!guard.assertOwned("mall_goods", id, "修改商品")) {
            throw new BusinessException(400, "商品不存在");
        }
        return Result.ok(MerchantResults.unwrap(goodsClient.update(payload(merchantId, body))));
    }

    @Operation(summary = "商品上下架：1上架0下架2草稿")
    @Log(title = "商家商品", type = "UPDATE")
    @PutMapping("/goods/status")
    public Result<Map<String, Object>> changeStatus(@RequestBody Map<String, Object> body) {
        Long merchantId = guard.currentMerchantId();
        Long id = asLong(body.get("id"));
        Integer status = asInt(body.get("status"));
        if (id == null || status == null) {
            throw new BusinessException(400, "参数错误：id 与 status 必填");
        }
        if (!guard.assertOwned("mall_goods", id, "修改商品状态")) {
            throw new BusinessException(400, "商品不存在");
        }
        return Result.ok(MerchantResults.unwrap(goodsClient.changeStatus(merchantId, id, status)));
    }

    @Operation(summary = "SKU 库存维护")
    @Log(title = "商家商品", type = "UPDATE")
    @PutMapping("/sku/stock")
    public Result<Map<String, Object>> updateStock(@RequestBody Map<String, Object> body) {
        Long merchantId = guard.currentMerchantId();
        Long skuId = asLong(body.get("skuId"));
        Integer stock = asInt(body.get("stock"));
        if (skuId == null || stock == null) {
            throw new BusinessException(400, "参数错误：skuId 与 stock 必填");
        }
        if (stock < 0) {
            throw new BusinessException(400, "库存不能为负数");
        }
        if (!guard.assertSkuOwned(skuId, "修改库存")) {
            throw new BusinessException(400, "SKU 不存在");
        }
        return Result.ok(MerchantResults.unwrap(goodsClient.updateStock(merchantId, skuId, stock)));
    }

    /** 复制一份请求体并强制覆盖 merchantId，避免前端传别人的店铺 */
    private Map<String, Object> payload(Long merchantId, Map<String, Object> body) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (body != null) {
            data.putAll(body);
        }
        data.put("merchantId", merchantId);
        return data;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asInt(Object value) {
        Long v = asLong(value);
        return v == null ? null : v.intValue();
    }
}
