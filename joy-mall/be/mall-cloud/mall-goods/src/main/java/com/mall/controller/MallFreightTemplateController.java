package com.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.FreightTemplatePageDTO;
import com.mall.dto.FreightTemplateSaveDTO;
import com.mall.entity.MallFreightTemplate;
import com.mall.service.MallFreightTemplateService;
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

import java.util.List;
import java.util.Map;

/**
 * 运费模板后台（V12）：模板与地区规则维护、设为默认。
 *
 * <p>与商品/店铺的绑定关系：商品级 {@code mall_goods.freight_template_id} 优先级最高，
 * 店铺级 {@code mall_merchant.freight_template_id} 次之，全局默认兜底。
 */
@Tag(name = "运费模板", description = "运费模板与地区规则")
@RestController
@RequestMapping("/mall_freight_template")
public class MallFreightTemplateController {

    private final MallFreightTemplateService freightService;

    public MallFreightTemplateController(MallFreightTemplateService freightService) {
        this.freightService = freightService;
    }

    @Operation(summary = "分页查询运费模板")
    @PostMapping("/page")
    public Result<Page<MallFreightTemplate>> page(@RequestBody FreightTemplatePageDTO dto) {
        return Result.ok(freightService.pageQuery(dto.getKeyword(), dto.getStatus(),
                dto.getPageNum(), dto.getPageSize()));
    }

    @Operation(summary = "启用中的模板下拉")
    @GetMapping("/options")
    public Result<List<Map<String, Object>>> options() {
        return Result.ok(freightService.options());
    }

    @Operation(summary = "模板详情（含地区规则）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return Result.ok(freightService.detail(id));
    }

    @Operation(summary = "新增运费模板")
    @Log(title = "运费模板", type = "INSERT")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody FreightTemplateSaveDTO dto) {
        dto.setId(null);
        freightService.saveTemplate(dto);
        return Result.ok();
    }

    @Operation(summary = "修改运费模板（规则全量覆盖）")
    @Log(title = "运费模板", type = "UPDATE")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody FreightTemplateSaveDTO dto) {
        freightService.saveTemplate(dto);
        return Result.ok();
    }

    @Operation(summary = "设为全局默认模板")
    @Log(title = "运费模板", type = "UPDATE")
    @PostMapping("/default")
    public Result<Void> setDefault(@RequestBody Map<String, Long> body) {
        freightService.setDefault(body.get("id"));
        return Result.ok();
    }

    @Operation(summary = "删除运费模板（默认模板/被商品引用不可删）")
    @Log(title = "运费模板", type = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        freightService.removeTemplate(id);
        return Result.ok();
    }
}
