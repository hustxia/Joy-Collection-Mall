package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.service.MallPriceRemindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * C 端降价提醒（V13 购物车营销）：订阅 / 我的订阅 / 取消。
 *
 * <p>「同一会员同一商品只触发一次」由唯一键 + 状态机保证，重复订阅只更新期望价。
 */
@Tag(name = "商城前台-降价提醒", description = "订阅商品降价通知")
@RestController
@RequestMapping("/shop/price-remind")
public class ShopPriceRemindController {

    private final MallPriceRemindService priceRemindService;

    public ShopPriceRemindController(MallPriceRemindService priceRemindService) {
        this.priceRemindService = priceRemindService;
    }

    @Operation(summary = "订阅降价提醒")
    @PostMapping
    public Result<Void> subscribe(@RequestBody SubscribeDTO dto) {
        if (dto == null || dto.getGoodsId() == null) {
            throw new BusinessException(400, "参数错误：goodsId 必填");
        }
        priceRemindService.subscribe(UserContext.getUserId(), dto.getGoodsId(),
                dto.getSkuId(), dto.getExpectedPrice());
        return Result.ok();
    }

    @Operation(summary = "我的降价提醒")
    @GetMapping
    public Result<List<Map<String, Object>>> mine() {
        return Result.ok(priceRemindService.myReminds(UserContext.getUserId()));
    }

    @Operation(summary = "取消订阅")
    @DeleteMapping("/{id}")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        priceRemindService.cancel(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Data
    public static class SubscribeDTO {
        private Long goodsId;
        private Long skuId;
        /** 期望价（分） */
        private Long expectedPrice;
    }
}
