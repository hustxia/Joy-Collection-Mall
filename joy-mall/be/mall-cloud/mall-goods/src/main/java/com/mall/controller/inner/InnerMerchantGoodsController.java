package com.mall.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.entity.MallGoods;
import com.mall.entity.MallSku;
import com.mall.mapper.MallGoodsMapper;
import com.mall.mapper.MallSkuMapper;
import com.mall.service.MallGoodsService;
import com.mall.service.MallSkuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品域商家内部端点（V11，{@code /inner/**} 免鉴权、只允许服务间直连）。
 *
 * <p>唯一的商家入口在商家域（{@code /merchant-admin/goods/**}）：那边做令牌与归属校验，
 * 这里只认显式传入的 {@code merchantId}，并且<b>每条 SQL 都带 merchant_id 条件</b>——
 * 内部端点不鉴权，所以数据边界只能靠参数和 WHERE 双重锁定。
 */
@RestController
@RequestMapping("/inner/merchant")
public class InnerMerchantGoodsController {

    /** 商品状态：上架 */
    private static final int ON_SALE = 1;

    private final MallGoodsService goodsService;
    private final MallSkuService skuService;
    private final MallGoodsMapper goodsMapper;
    private final MallSkuMapper skuMapper;

    public InnerMerchantGoodsController(MallGoodsService goodsService, MallSkuService skuService,
                                        MallGoodsMapper goodsMapper, MallSkuMapper skuMapper) {
        this.goodsService = goodsService;
        this.skuService = skuService;
        this.goodsMapper = goodsMapper;
        this.skuMapper = skuMapper;
    }

    /** 本店商品分页 */
    @GetMapping("/goods/page")
    public Map<String, Object> page(@RequestParam("merchantId") Long merchantId,
                                    @RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "status", required = false) Integer status,
                                    @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                    @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        requireMerchant(merchantId);
        LambdaQueryWrapper<MallGoods> wrapper = new LambdaQueryWrapper<MallGoods>()
                .eq(MallGoods::getMerchantId, merchantId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(MallGoods::getName, keyword.trim());
        }
        if (status != null) {
            wrapper.eq(MallGoods::getStatus, status);
        }
        wrapper.orderByDesc(MallGoods::getId);
        Page<MallGoods> page = goodsService.page(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", page.getRecords());
        data.put("total", page.getTotal());
        data.put("pageNum", page.getCurrent());
        data.put("pageSize", page.getSize());
        return data;
    }

    /** 商品详情（含 SKU）：不属于该商家直接 403 */
    @GetMapping("/goods/{id}")
    public Map<String, Object> detail(@PathVariable("id") Long id,
                                      @RequestParam("merchantId") Long merchantId) {
        requireMerchant(merchantId);
        MallGoods goods = requireOwnedGoods(id, merchantId);
        List<MallSku> skus = skuService.list(new LambdaQueryWrapper<MallSku>()
                .eq(MallSku::getGoodsId, goods.getId())
                .orderByAsc(MallSku::getId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goods", goods);
        data.put("skus", skus);
        return data;
    }

    /** 新增商品（含 SKU 全量插入） */
    @PostMapping("/goods")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Long merchantId = asLong(body.get("merchantId"));
        requireMerchant(merchantId);
        MallGoods goods = new MallGoods();
        goods.setMerchantId(merchantId);
        applyGoods(goods, body, true);
        goodsService.save(goods);
        saveSkus(goods.getId(), body);
        refreshPriceRange(goods.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", goods.getId());
        return data;
    }

    /** 修改商品：只允许改本店商品；SKU 以本次提交为准（先删后插） */
    @PutMapping("/goods")
    public Map<String, Object> update(@RequestBody Map<String, Object> body) {
        Long merchantId = asLong(body.get("merchantId"));
        requireMerchant(merchantId);
        Long id = asLong(body.get("id"));
        if (id == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        MallGoods goods = requireOwnedGoods(id, merchantId);
        applyGoods(goods, body, false);
        goodsService.updateById(goods);
        if (body.get("skus") instanceof List<?>) {
            skuMapper.delete(new LambdaQueryWrapper<MallSku>().eq(MallSku::getGoodsId, id));
            saveSkus(id, body);
        }
        refreshPriceRange(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        return data;
    }

    /** 上下架：1 上架 / 0 下架 / 2 草稿 */
    @PutMapping("/goods/status")
    public Map<String, Object> changeStatus(@RequestParam("merchantId") Long merchantId,
                                            @RequestParam("id") Long id,
                                            @RequestParam("status") Integer status) {
        requireMerchant(merchantId);
        if (status == null || status < 0 || status > 2) {
            throw new BusinessException(400, "状态取值不正确");
        }
        requireOwnedGoods(id, merchantId);
        goodsMapper.update(null, new LambdaUpdateWrapper<MallGoods>()
                .eq(MallGoods::getId, id)
                .eq(MallGoods::getMerchantId, merchantId)
                .set(MallGoods::getStatus, status));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("status", status);
        return data;
    }

    /** SKU 库存维护：SKU → 商品 → 商家 双重校验 */
    @PutMapping("/sku/stock")
    public Map<String, Object> updateStock(@RequestParam("merchantId") Long merchantId,
                                           @RequestParam("skuId") Long skuId,
                                           @RequestParam("stock") Integer stock) {
        requireMerchant(merchantId);
        if (stock == null || stock < 0) {
            throw new BusinessException(400, "库存不能为负数");
        }
        MallSku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BusinessException(400, "SKU 不存在");
        }
        requireOwnedGoods(sku.getGoodsId(), merchantId);
        skuMapper.update(null, new LambdaUpdateWrapper<MallSku>()
                .eq(MallSku::getId, skuId)
                .set(MallSku::getStock, stock));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skuId", skuId);
        data.put("stock", stock);
        return data;
    }

    private MallGoods requireOwnedGoods(Long id, Long merchantId) {
        if (id == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        MallGoods goods = goodsMapper.selectById(id);
        if (goods == null) {
            throw new BusinessException(400, "商品不存在");
        }
        if (goods.getMerchantId() == null || !goods.getMerchantId().equals(merchantId)) {
            throw new BusinessException(403, "无权访问其他店铺的商品");
        }
        return goods;
    }

    private void requireMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new BusinessException(400, "参数错误：merchantId 必填");
        }
    }

    private void applyGoods(MallGoods goods, Map<String, Object> body, boolean creating) {
        String name = asString(body.get("name"));
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "商品名称不能为空");
        }
        goods.setName(name.trim());
        if (creating || body.containsKey("goodsNo")) {
            String goodsNo = asString(body.get("goodsNo"));
            goods.setGoodsNo(goodsNo == null || goodsNo.isBlank() ? newGoodsNo() : goodsNo.trim());
        }
        if (body.containsKey("categoryId")) {
            goods.setCategoryId(asLong(body.get("categoryId")));
        }
        if (body.containsKey("brandId")) {
            goods.setBrandId(asLong(body.get("brandId")));
        }
        if (body.containsKey("subTitle")) {
            goods.setSubTitle(asString(body.get("subTitle")));
        }
        if (body.containsKey("mainImage")) {
            goods.setMainImage(asString(body.get("mainImage")));
        }
        if (body.containsKey("sliderImages")) {
            goods.setSliderImages(asString(body.get("sliderImages")));
        }
        if (body.containsKey("detail")) {
            goods.setDetail(asString(body.get("detail")));
        }
        if (body.containsKey("freightTemplateId")) {
            goods.setFreightTemplateId(asLong(body.get("freightTemplateId")));
        }
        if (creating) {
            Integer status = asInt(body.get("status"));
            goods.setStatus(status == null ? ON_SALE : status);
            goods.setSales(0);
        } else if (body.containsKey("status")) {
            goods.setStatus(asInt(body.get("status")));
        }
        if (body.containsKey("remark")) {
            goods.setRemark(asString(body.get("remark")));
        }
        if (goods.getPriceMin() == null) {
            goods.setPriceMin(0L);
        }
        if (goods.getPriceMax() == null) {
            goods.setPriceMax(0L);
        }
    }

    @SuppressWarnings("unchecked")
    private void saveSkus(Long goodsId, Map<String, Object> body) {
        Object raw = body.get("skus");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> skuMap = (Map<String, Object>) map;
            MallSku sku = new MallSku();
            sku.setGoodsId(goodsId);
            String skuCode = asString(skuMap.get("skuCode"));
            sku.setSkuCode(skuCode == null || skuCode.isBlank() ? newSkuCode() : skuCode.trim());
            sku.setSpecJson(asString(skuMap.get("specJson")));
            Long price = asLong(skuMap.get("price"));
            sku.setPrice(price == null ? 0L : Math.max(price, 0L));
            Integer stock = asInt(skuMap.get("stock"));
            sku.setStock(stock == null ? 0 : Math.max(stock, 0));
            sku.setSales(0);
            sku.setImage(asString(skuMap.get("image")));
            Integer weight = asInt(skuMap.get("weight"));
            sku.setWeight(weight == null ? 0 : Math.max(weight, 0));
            Integer status = asInt(skuMap.get("status"));
            sku.setStatus(status == null ? 1 : status);
            skuMapper.insert(sku);
        }
    }

    /** 商品区间价由 SKU 推导：列表页展示「¥最低起」靠这两列，不能靠前端算 */
    private void refreshPriceRange(Long goodsId) {
        List<MallSku> skus = skuService.list(new LambdaQueryWrapper<MallSku>()
                .eq(MallSku::getGoodsId, goodsId)
                .eq(MallSku::getStatus, 1));
        if (skus.isEmpty()) {
            return;
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (MallSku sku : skus) {
            long price = sku.getPrice() == null ? 0L : sku.getPrice();
            min = Math.min(min, price);
            max = Math.max(max, price);
        }
        goodsMapper.update(null, new LambdaUpdateWrapper<MallGoods>()
                .eq(MallGoods::getId, goodsId)
                .set(MallGoods::getPriceMin, min)
                .set(MallGoods::getPriceMax, max));
    }

    private String newGoodsNo() {
        return "MG" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);
    }

    private String newSkuCode() {
        return "MS" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 999);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
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
