package com.mall.controller.shop;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.feign.GoodsInnerClient;
import com.mall.common.feign.dto.GoodsBrief;
import com.mall.common.result.Result;
import com.mall.entity.MallFavorite;
import com.mall.service.MallFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * C 端商品收藏（V7）：memberId 只从 UserContext 取，入参不接收 memberId。
 * 列表商品名/主图/价格经商品域 /inner 接口批量补齐，商品已下架照返由前端置灰。
 */
@Tag(name = "商城前台-商品收藏", description = "收藏切换/查询/列表")
@RestController
@RequestMapping("/shop/favorite")
public class ShopFavoriteController {

    private static final int MAX_PAGE_SIZE = 100;

    private final MallFavoriteService mallFavoriteService;
    private final GoodsInnerClient goodsInnerClient;

    public ShopFavoriteController(MallFavoriteService mallFavoriteService, GoodsInnerClient goodsInnerClient) {
        this.mallFavoriteService = mallFavoriteService;
        this.goodsInnerClient = goodsInnerClient;
    }

    @Operation(summary = "收藏切换（幂等）：已收藏→取消返回 false，未收藏/已取消→收藏返回 true")
    @PostMapping("/{goodsId}")
    public Result<Map<String, Object>> toggle(@PathVariable("goodsId") Long goodsId) {
        boolean favorited = mallFavoriteService.toggle(requireMemberId(), goodsId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("favorited", favorited);
        return Result.ok(data);
    }

    @Operation(summary = "是否已收藏该商品")
    @GetMapping("/check/{goodsId}")
    public Result<Map<String, Object>> check(@PathVariable("goodsId") Long goodsId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("favorited", mallFavoriteService.isFavorited(requireMemberId(), goodsId));
        return Result.ok(data);
    }

    @Operation(summary = "我的收藏列表（按收藏时间倒序，带商品名/主图/价格）")
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(value = "pageNum", defaultValue = "1") int pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Long memberId = requireMemberId();
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<MallFavorite> page = mallFavoriteService.pageOf(memberId, pageNum, size);

        List<Long> goodsIds = page.getRecords().stream().map(MallFavorite::getGoodsId).toList();
        Map<Long, GoodsBrief> goodsMap = goodsIds.isEmpty() ? Map.of()
                : goodsInnerClient.goodsByIds(goodsIds).stream()
                        .collect(Collectors.toMap(GoodsBrief::getId, Function.identity()));

        List<Map<String, Object>> rows = page.getRecords().stream().map(favorite -> {
            GoodsBrief goods = goodsMap.get(favorite.getGoodsId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(favorite.getId()));
            row.put("goodsId", String.valueOf(favorite.getGoodsId()));
            row.put("goodsName", goods == null ? null : goods.getName());
            row.put("mainImage", goods == null ? null : goods.getMainImage());
            row.put("priceMin", goods == null ? null : goods.getPriceMin());
            row.put("goodsStatus", goods == null ? 0 : goods.getStatus());
            return row;
        }).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", page.getTotal());
        return Result.ok(data);
    }

    private Long requireMemberId() {
        Long memberId = UserContext.getUserId();
        if (memberId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return memberId;
    }
}
