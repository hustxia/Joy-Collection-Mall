package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.mall.dto.MallWithdrawalPageDTO;
import com.mall.entity.MallWithdrawal;
import com.mall.service.MallWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

/**
 * 分销员提现后台管理
 */
@Tag(name = "分销提现管理", description = "提现审核/打款")
@RestController
@RequestMapping("/mall_withdrawal")
public class MallWithdrawalController {

    private final MallWithdrawalService withdrawalService;

    public MallWithdrawalController(MallWithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @Operation(summary = "分页查询提现单")
    @PostMapping("/page")
    public Result<Page<MallWithdrawal>> page(@RequestBody MallWithdrawalPageDTO dto) {
        Page<MallWithdrawal> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<MallWithdrawal> wrapper = new LambdaQueryWrapper<>();
        if (dto.getStatus() != null) {
            wrapper.eq(MallWithdrawal::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(MallWithdrawal::getId);
        withdrawalService.page(page, wrapper);
        return Result.ok(page);
    }

    @Operation(summary = "审核提现：通过→已打款，驳回→退回")
    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody WithdrawalAuditDTO dto) {
        if (dto == null || dto.getId() == null || dto.getPass() == null) {
            return Result.fail(400, "参数错误：id/pass 必填");
        }
        Long auditBy = UserContext.getUserId();
        withdrawalService.audit(dto.getId(), dto.getPass(), auditBy, dto.getRemark());
        return Result.ok();
    }

    @Data
    public static class WithdrawalAuditDTO {
        private Long id;
        private Boolean pass;
        private String remark;
    }
}
