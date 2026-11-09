package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.feign.dto.PromotionCalcRequest;
import com.mall.common.feign.dto.PromotionCalcResult;
import com.mall.common.result.Result;
import com.mall.service.MallPromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * C 端促销试算（V13）。
 *
 * <p>三个入口共用营销域的同一段计算代码：
 * <ul>
 *   <li>{@code POST /shop/promotion/calculate} —— 购物车/商品页试算；</li>
 *   <li>{@code GET /shop/promotion/available} —— 商品卡/详情页的活动价与倒计时（匿名可读）；</li>
 *   <li>订单确认页与下单提交走 {@code /inner/promotion/calc}（内部接口，同一 Service）。</li>
 * </ul>
 *
 * <p>注意路径前缀 {@code /shop/promotion} 与 V9 的「推广商品」{@code /shop/promotion/goods} 同前缀不同路径，
 * 二者互不影响（分布式的分销推广 vs 本类的促销活动）。
 */
@Tag(name = "商城前台-促销试算", description = "活动试算 / 商品可用活动")
@RestController
@RequestMapping("/shop/promotion")
public class ShopPromotionEngineController {

    private final MallPromotionService promotionService;

    public ShopPromotionEngineController(MallPromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Operation(summary = "促销试算（活动优惠，不含券/积分/运费）")
    @PostMapping("/calculate")
    public Result<PromotionCalcResult> calculate(@RequestBody CalculateDTO dto) {
        PromotionCalcRequest request = new PromotionCalcRequest();
        Long memberId = UserContext.getUserId();
        request.setMemberId(memberId);
        request.setStackMode(dto == null ? null : dto.getStackMode());
        if (dto != null && dto.getItems() != null) {
            for (CalculateDTO.Item src : dto.getItems()) {
                if (src == null || src.getSkuId() == null) {
                    continue;
                }
                PromotionCalcRequest.Item item = new PromotionCalcRequest.Item();
                item.setSkuId(src.getSkuId());
                item.setGoodsId(src.getGoodsId());
                item.setCategoryId(src.getCategoryId());
                item.setMerchantId(src.getMerchantId());
                item.setPrice(src.getPrice() == null ? 0L : src.getPrice());
                item.setQuantity(src.getQuantity() == null ? 1 : src.getQuantity());
                request.getItems().add(item);
            }
        }
        return Result.ok(promotionService.calculate(request));
    }

    @Operation(summary = "商品可用活动（带倒计时秒数，商品卡/详情页）")
    @GetMapping("/available")
    public Result<List<Map<String, Object>>> available(@RequestParam("goodsId") Long goodsId) {
        return Result.ok(promotionService.available(goodsId));
    }

    @Data
    public static class CalculateDTO {
        /** 可选：best/stack，空=服务端配置 */
        private String stackMode;
        private List<Item> items = new ArrayList<>();

        @Data
        public static class Item {
            private Long skuId;
            private Long goodsId;
            private Long categoryId;
            private Long merchantId;
            /** 单价（分） */
            private Long price;
            private Integer quantity;
        }
    }
}
