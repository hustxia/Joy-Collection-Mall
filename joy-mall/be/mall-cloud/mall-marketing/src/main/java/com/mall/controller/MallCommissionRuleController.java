package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.dto.MallCommissionRulePageDTO;
import com.mall.dto.MallCommissionRuleSaveDTO;
import com.mall.entity.MallCommissionRule;
import com.mall.service.MallCommissionRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 佣金规则后台管理
 */
@Tag(name = "佣金规则管理", description = "佣金比例/固定佣金配置")
@RestController
@RequestMapping("/mall_commission_rule")
public class MallCommissionRuleController {

    private final MallCommissionRuleService commissionRuleService;

    public MallCommissionRuleController(MallCommissionRuleService commissionRuleService) {
        this.commissionRuleService = commissionRuleService;
    }

    @Operation(summary = "分页查询佣金规则")
    @PostMapping("/page")
    public Result<Page<MallCommissionRule>> page(@RequestBody MallCommissionRulePageDTO dto) {
        Page<MallCommissionRule> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<MallCommissionRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(MallCommissionRule::getId);
        commissionRuleService.page(page, wrapper);
        return Result.ok(page);
    }

    @Operation(summary = "新增/修改佣金规则")
    @PostMapping("/save")
    public Result<Void> save(@RequestBody MallCommissionRuleSaveDTO dto) {
        MallCommissionRule entity = new MallCommissionRule();
        BeanUtils.copyProperties(dto, entity);
        if (dto.getId() == null) {
            commissionRuleService.save(entity);
        } else {
            commissionRuleService.updateById(entity);
        }
        return Result.ok();
    }
}
