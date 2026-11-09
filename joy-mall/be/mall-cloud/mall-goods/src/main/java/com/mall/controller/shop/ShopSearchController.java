package com.mall.controller.shop;

import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.mall.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 商城前台搜索（V14，REQ-P1-04）。
 *
 * <p>全部 GET 只读、匿名可访问（AuthContextFilter 的 C 端匿名白名单已放行 {@code GET /shop/search}）：
 * <ul>
 *   <li>{@code GET /shop/search?keyword=}：标题/副标题/品牌/类目命中 + 拼音 + 同义词 + 错字纠错；</li>
 *   <li>{@code GET /shop/search/suggest?keyword=}：联想下拉 10 条；</li>
 *   <li>{@code GET /shop/search/hot}：热搜词（近 7 天搜索日志统计，空则词表权重兜底）。</li>
 * </ul>
 * 无结果时 {@code fallback=true}，records 为同类热销，前端不应再展示「空状态白屏」。
 */
@Tag(name = "商城前台-搜索", description = "商品搜索/联想/热搜")
@RestController
@RequestMapping("/shop/search")
public class ShopSearchController {

    private final SearchService searchService;

    public ShopSearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "商品搜索（拼音/同义词/错字纠错 + 无结果同类热销兜底）")
    @GetMapping
    public Result<Map<String, Object>> search(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "12") long pageSize) {
        return Result.ok(searchService.search(keyword, pageNum, pageSize, UserContext.getUserId()));
    }

    @Operation(summary = "搜索联想：下拉 10 条")
    @GetMapping("/suggest")
    public Result<List<String>> suggest(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(searchService.suggest(keyword));
    }

    @Operation(summary = "热搜词（近 7 天搜索日志 Top10）")
    @GetMapping("/hot")
    public Result<List<Map<String, Object>>> hot() {
        return Result.ok(searchService.hot());
    }
}
