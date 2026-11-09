package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.entity.MallPromotionGoods;
import com.mall.service.MallPromotionGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * C 端推广商品（V9）：可推广商品列表（带佣金标签）
 */
@Tag(name = "商城前台-推广商品", description = "可推广商品列表")
@RestController
@RequestMapping("/shop/promotion")
public class ShopPromotionController {

    private final MallPromotionGoodsService promotionGoodsService;

    public ShopPromotionController(MallPromotionGoodsService promotionGoodsService) {
        this.promotionGoodsService = promotionGoodsService;
    }

    @Operation(summary = "可推广商品列表（状态启用，带佣金比例）")
    @GetMapping("/goods")
    public Result<Page<MallPromotionGoods>> goods(@RequestParam(defaultValue = "1") int pageNum,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        Page<MallPromotionGoods> page = new Page<>(pageNum, pageSize);
        promotionGoodsService.page(page, new LambdaQueryWrapper<MallPromotionGoods>()
                .eq(MallPromotionGoods::getStatus, 1)
                .orderByDesc(MallPromotionGoods::getId));
        return Result.ok(page);
    }
}
