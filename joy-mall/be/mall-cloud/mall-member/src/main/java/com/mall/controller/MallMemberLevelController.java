package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.MallMemberLevelPageDTO;
import com.mall.dto.MallMemberLevelSaveDTO;
import com.mall.entity.MallMemberLevel;
import com.mall.service.MallMemberLevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员等级后台（V13）：等级 CRUD，折扣率与每月包邮次数都在这里配。
 */
@Tag(name = "会员等级", description = "等级阈值 / 折扣率 / 每月包邮次数")
@RestController
@RequestMapping("/mall_member_level")
public class MallMemberLevelController {

    private final MallMemberLevelService levelService;

    public MallMemberLevelController(MallMemberLevelService levelService) {
        this.levelService = levelService;
    }

    @Operation(summary = "分页查询等级")
    @PostMapping("/page")
    public Result<Page<Map<String, Object>>> page(@RequestBody MallMemberLevelPageDTO dto) {
        LambdaQueryWrapper<MallMemberLevel> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.like(MallMemberLevel::getName, dto.getKeyword().trim());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MallMemberLevel::getStatus, dto.getStatus());
        }
        wrapper.orderByAsc(MallMemberLevel::getGrowthThreshold).orderByAsc(MallMemberLevel::getLevelNo);
        Page<MallMemberLevel> page = levelService.page(
                new Page<>(dto.getPageNum(), Math.min(dto.getPageSize(), 100)), wrapper);
        Page<Map<String, Object>> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::rowOf).toList());
        return Result.ok(voPage);
    }

    @Operation(summary = "启用中的等级下拉")
    @GetMapping("/options")
    public Result<List<Map<String, Object>>> options() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MallMemberLevel level : levelService.enabledLevels()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(level.getId()));
            row.put("name", level.getName());
            row.put("growthThreshold", level.getGrowthThreshold());
            row.put("discountRate", level.getDiscountRate());
            rows.add(row);
        }
        return Result.ok(rows);
    }

    @Operation(summary = "等级详情")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        MallMemberLevel level = levelService.getById(id);
        return Result.ok(level == null ? null : rowOf(level));
    }

    @Operation(summary = "新增等级")
    @Log(title = "会员等级", type = "INSERT")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody MallMemberLevelSaveDTO dto) {
        dto.setId(null);
        levelService.saveLevel(dto);
        return Result.ok();
    }

    @Operation(summary = "修改等级")
    @Log(title = "会员等级", type = "UPDATE")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody MallMemberLevelSaveDTO dto) {
        levelService.saveLevel(dto);
        return Result.ok();
    }

    @Operation(summary = "删除等级（有会员在用不可删）")
    @Log(title = "会员等级", type = "DELETE")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        levelService.removeLevel(id);
        return Result.ok();
    }

    private Map<String, Object> rowOf(MallMemberLevel level) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", String.valueOf(level.getId()));
        row.put("name", level.getName());
        row.put("levelNo", level.getLevelNo());
        row.put("growthThreshold", level.getGrowthThreshold());
        row.put("discountRate", level.getDiscountRate());
        row.put("freeShippingCount", level.getFreeShippingCount());
        row.put("benefitsJson", level.getBenefitsJson());
        row.put("icon", level.getIcon());
        row.put("status", level.getStatus());
        row.put("sort", level.getSort());
        row.put("createTime", level.getCreateTime());
        return row;
    }
}
