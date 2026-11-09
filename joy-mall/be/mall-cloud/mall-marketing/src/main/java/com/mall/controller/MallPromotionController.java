package com.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.MallPromotionPageDTO;
import com.mall.dto.MallPromotionSaveDTO;
import com.mall.service.MallPromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 促销活动后台（V13）：活动 CRUD + 上下线 + 类型字典。
 *
 * <p>与 {@code /mall_coupon}（券）并列，互不影响：券走「领取-核销」，活动走「时间窗自动生效」。
 */
@Tag(name = "促销活动", description = "满减/满折/限时折扣/第二件半价/套装/加价购")
@RestController
@RequestMapping("/mall_promotion")
public class MallPromotionController {

    private final MallPromotionService promotionService;

    public MallPromotionController(MallPromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Operation(summary = "分页查询活动")
    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestBody MallPromotionPageDTO dto) {
        return Result.ok(promotionService.pageQuery(dto));
    }

    @Operation(summary = "活动详情（含规则与作用范围）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(promotionService.detail(id));
    }

    @Operation(summary = "类型/范围字典（前端下拉用）")
    @GetMapping("/dict")
    public Result<Map<String, Object>> dict() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("types", List.of(
                Map.of("value", 1, "label", "满减"),
                Map.of("value", 2, "label", "满折"),
                Map.of("value", 3, "label", "限时折扣"),
                Map.of("value", 4, "label", "第二件半价"),
                Map.of("value", 5, "label", "套装"),
                Map.of("value", 6, "label", "加价购")));
        data.put("scopes", List.of(
                Map.of("value", 1, "label", "全场"),
                Map.of("value", 2, "label", "类目"),
                Map.of("value", 3, "label", "商品"),
                Map.of("value", 4, "label", "店铺")));
        data.put("stackMode", promotionService.stackMode());
        return Result.ok(data);
    }

    @Operation(summary = "新增活动")
    @Log(title = "促销活动", type = "INSERT")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody MallPromotionSaveDTO dto) {
        dto.setId(null);
        promotionService.savePromotion(dto);
        return Result.ok();
    }

    @Operation(summary = "修改活动（作用范围全量覆盖）")
    @Log(title = "促销活动", type = "UPDATE")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MallPromotionSaveDTO dto) {
        promotionService.savePromotion(dto);
        return Result.ok();
    }

    @Operation(summary = "上下线（body 传 id + status）")
    @Log(title = "促销活动", type = "UPDATE")
    @PostMapping("/status")
    public Result<Void> status(@RequestBody Map<String, Long> body) {
        Long id = body.get("id");
        Long status = body.get("status");
        promotionService.setStatus(id, status == null ? null : status.intValue());
        return Result.ok();
    }

    @Operation(summary = "删除活动")
    @Log(title = "促销活动", type = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        promotionService.removePromotion(id);
        return Result.ok();
    }
}
