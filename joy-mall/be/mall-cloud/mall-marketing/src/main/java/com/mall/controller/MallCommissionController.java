package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.result.Result;
import com.mall.dto.MallCommissionRecordPageDTO;
import com.mall.entity.MallCommissionRecord;
import com.mall.service.MallCommissionRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 佣金记录后台管理 + 手动结算
 */
@Tag(name = "佣金管理", description = "佣金明细/手动结算")
@RestController
@RequestMapping("/mall_commission")
public class MallCommissionController {

    private final MallCommissionRecordService commissionRecordService;

    public MallCommissionController(MallCommissionRecordService commissionRecordService) {
        this.commissionRecordService = commissionRecordService;
    }

    @Operation(summary = "分页查询佣金明细")
    @PostMapping("/page")
    public Result<Page<MallCommissionRecord>> page(@RequestBody MallCommissionRecordPageDTO dto) {
        Page<MallCommissionRecord> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<MallCommissionRecord> wrapper = new LambdaQueryWrapper<>();
        if (dto.getDistributorId() != null) {
            wrapper.eq(MallCommissionRecord::getDistributorId, dto.getDistributorId());
        }
        if (dto.getStatus() != null) {
            wrapper.eq(MallCommissionRecord::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(MallCommissionRecord::getId);
        commissionRecordService.page(page, wrapper);
        return Result.ok(page);
    }

    @Operation(summary = "手动结算（幂等，重复安全）：供订单完成钩子之外的补结算")
    @PostMapping("/settle")
    public Result<Void> settle(@RequestParam Long distributorId,
                               @RequestParam Long orderId,
                               @RequestParam Long memberId,
                               @RequestParam Long goodsId,
                               @RequestParam Long payAmount) {
        commissionRecordService.settle(distributorId, orderId, memberId, goodsId, payAmount);
        return Result.ok();
    }
}
