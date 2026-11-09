package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.result.Result;
import com.mall.dto.ShopGoodsVO;
import com.mall.dto.ShopSkuVO;
import com.mall.entity.MallBanner;
import com.mall.entity.MallBrand;
import com.mall.entity.MallCategory;
import com.mall.entity.MallGoods;
import com.mall.entity.MallSku;
import com.mall.service.MallBannerService;
import com.mall.service.MallBrandService;
import com.mall.service.MallCategoryService;
import com.mall.service.MallGoodsService;
import com.mall.service.MallReviewService;
import com.mall.service.MallSkuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.mall.common.feign.MerchantInnerClient;
import com.mall.common.feign.dto.MerchantBrief;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城前台公开接口（PC 端 9432 / H5 端 9433 专用）。
 *
 * <p>全部只读 GET，/shop 前缀在 AuthContextFilter 白名单中，买家无需登录即可浏览。
 * 与后台 admin 接口（/mall_*，需权限点）完全隔离——前台拿不到任何写接口与运营数据。
 * 状态口径：只出 status=1（上架/启用）的数据。
 */
@Tag(name = "商城前台", description = "买家浏览侧公开接口")
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Autowired
    private MallGoodsService mallGoodsService;
    @Autowired
    private MallSkuService mallSkuService;
    @Autowired
    private MallBannerService mallBannerService;
    @Autowired
    private MallCategoryService mallCategoryService;
    @Autowired
    private MallBrandService mallBrandService;
    @Autowired
    private MallReviewService mallReviewService;
    @Autowired
    private MerchantInnerClient merchantInnerClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Operation(summary = "轮播图列表（status=1，sort 升序）")
    @GetMapping("/banners")
    public Result<List<MallBanner>> banners() {
        LambdaQueryWrapper<MallBanner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallBanner::getStatus, 1)
                .orderByAsc(MallBanner::getSort)
                .orderByAsc(MallBanner::getId);
        List<MallBanner> list = mallBannerService.list(wrapper);
        list.forEach(b -> b.setRemark(null));
        return Result.ok(list);
    }

    @Operation(summary = "启用类目树（status=1，仅含上架类目）")
    @GetMapping("/categories")
    public Result<List<Map<String, Object>>> categories() {
        LambdaQueryWrapper<MallCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallCategory::getStatus, 1).orderByAsc(MallCategory::getSort);
        List<MallCategory> all = mallCategoryService.list(wrapper);
        return Result.ok(buildTree(all, 0L));
    }

    @Operation(summary = "上架商品分页（keyword 模糊 / categoryId 含子类 / sort=sales|priceAsc|priceDesc）")
    @GetMapping("/goods")
    public Result<Page<ShopGoodsVO>> goods(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "12") long pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long merchantId) {
        LambdaQueryWrapper<MallGoods> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallGoods::getStatus, 1);
        // V10：店铺页按商家过滤
        if (merchantId != null) {
            wrapper.eq(MallGoods::getMerchantId, merchantId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(MallGoods::getName, keyword).or().like(MallGoods::getSubTitle, keyword));
        }
        if (categoryId != null) {
            // 一级类目要连同其子类目的商品一起出
            List<Long> ids = selfAndChildIds(categoryId);
            wrapper.in(MallGoods::getCategoryId, ids);
        }
        if ("sales".equals(sort)) {
            wrapper.orderByDesc(MallGoods::getSales);
        } else if ("priceAsc".equals(sort)) {
            wrapper.orderByAsc(MallGoods::getPriceMin);
        } else if ("priceDesc".equals(sort)) {
            wrapper.orderByDesc(MallGoods::getPriceMin);
        } else {
            wrapper.orderByDesc(MallGoods::getId);
        }
        Page<MallGoods> page = mallGoodsService.page(new Page<>(pageNum, Math.min(pageSize, 60)), wrapper);
        Page<ShopGoodsVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<ShopGoodsVO> records = page.getRecords().stream().map(this::toVo).toList();
        fillMerchantNames(records);
        voPage.setRecords(records);
        return Result.ok(voPage);
    }

    @Operation(summary = "商品详情（含上架 SKU 与轮播图，下架返回 data=null）")
    @GetMapping("/goods/{id}")
    public Result<ShopGoodsVO> detail(@PathVariable("id") Long id) {
        MallGoods goods = mallGoodsService.getById(id);
        if (goods == null || !Integer.valueOf(1).equals(goods.getStatus())) {
            return Result.ok(null);
        }
        ShopGoodsVO vo = toVo(goods);
        vo.setSliderImages(parseImages(goods.getSliderImages()));
        LambdaQueryWrapper<MallSku> skuWrapper = new LambdaQueryWrapper<>();
        skuWrapper.eq(MallSku::getGoodsId, id)
                .eq(MallSku::getStatus, 1)
                .orderByAsc(MallSku::getPrice);
        vo.setSkus(mallSkuService.list(skuWrapper).stream().map(sku -> {
            ShopSkuVO sv = new ShopSkuVO();
            sv.setId(sku.getId());
            sv.setSkuCode(sku.getSkuCode());
            sv.setSpecJson(sku.getSpecJson());
            sv.setPrice(sku.getPrice());
            sv.setStock(sku.getStock());
            sv.setSales(sku.getSales());
            sv.setImage(sku.getImage());
            return sv;
        }).toList());
        // V7：详情聚合评价概况（status=0 的平均分/总数，无评价时 0）
        vo.setRatingAvg(mallReviewService.avgRatingOf(id));
        vo.setRatingCount(mallReviewService.countOf(id));
        fillMerchantNames(List.of(vo));
        return Result.ok(vo);
    }

    // ===== 私有辅助 =====

    /**
     * V10：批量补店铺名。按 merchantId 去重后逐个取（去重后通常 1~3 次调用），
     * 商家域不可用时只落 null，不影响商品列表主信息。
     */
    private void fillMerchantNames(List<ShopGoodsVO> records) {
        java.util.Map<Long, String> cache = new java.util.HashMap<>();
        for (ShopGoodsVO vo : records) {
            Long merchantId = vo.getMerchantId() == null ? 1L : vo.getMerchantId();
            if (cache.containsKey(merchantId)) {
                vo.setMerchantName(cache.get(merchantId));
                continue;
            }
            String name = null;
            try {
                MerchantBrief brief = merchantInnerClient.merchant(merchantId);
                name = brief == null ? null : brief.getName();
            } catch (Exception ignored) {
                // 展示降级：店铺名缺失不影响商品浏览
            }
            cache.put(merchantId, name);
            vo.setMerchantName(name);
        }
    }

    private ShopGoodsVO toVo(MallGoods g) {
        ShopGoodsVO vo = new ShopGoodsVO();
        vo.setId(g.getId());
        vo.setMerchantId(g.getMerchantId() == null ? 1L : g.getMerchantId());
        vo.setGoodsNo(g.getGoodsNo());
        vo.setName(g.getName());
        vo.setSubTitle(g.getSubTitle());
        vo.setMainImage(g.getMainImage());
        vo.setDetail(g.getDetail());
        vo.setPriceMin(g.getPriceMin());
        vo.setPriceMax(g.getPriceMax());
        vo.setSales(g.getSales());
        vo.setCategoryId(g.getCategoryId());
        vo.setBrandId(g.getBrandId());
        if (g.getCategoryId() != null) {
            MallCategory c = mallCategoryService.getById(g.getCategoryId());
            vo.setCategoryName(c == null ? null : c.getName());
        }
        if (g.getBrandId() != null) {
            MallBrand b = mallBrandService.getById(g.getBrandId());
            vo.setBrandName(b == null ? null : b.getName());
        }
        return vo;
    }

    /** 自身 + 直接子类目 id（两级类目模型足够；更深层忽略） */
    private List<Long> selfAndChildIds(Long categoryId) {
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        LambdaQueryWrapper<MallCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MallCategory::getParentId, categoryId).eq(MallCategory::getStatus, 1);
        mallCategoryService.list(wrapper).forEach(c -> ids.add(c.getId()));
        return ids;
    }

    private List<Map<String, Object>> buildTree(List<MallCategory> all, Long parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (MallCategory c : all) {
            Long pid = c.getParentId() == null ? 0L : c.getParentId();
            if (!pid.equals(parentId)) {
                continue;
            }
            Map<String, Object> node = new HashMap<>();
            node.put("id", c.getId());
            node.put("parentId", c.getParentId());
            node.put("name", c.getName());
            node.put("icon", c.getIcon());
            node.put("level", c.getLevel());
            node.put("children", buildTree(all, c.getId()));
            tree.add(node);
        }
        return tree;
    }

    private List<String> parseImages(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
