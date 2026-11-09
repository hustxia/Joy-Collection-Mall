package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.result.Result;
import com.mall.dto.MallDistributorPageDTO;
import com.mall.entity.MallDistributor;
import com.mall.service.MallDistributorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 分销员后台管理
 */
@Tag(name = "分销员管理", description = "分销员审核/启停")
@RestController
@RequestMapping("/mall_distributor")
public class MallDistributorController {

    private final MallDistributorService distributorService;

    public MallDistributorController(MallDistributorService distributorService) {
        this.distributorService = distributorService;
    }

    @Operation(summary = "分页查询分销员")
    @PostMapping("/page")
    public Result<Page<MallDistributor>> page(@RequestBody MallDistributorPageDTO dto) {
        Page<MallDistributor> page = new Page<>(dto.getPageNum(), dto.getPageSize());
        LambdaQueryWrapper<MallDistributor> wrapper = new LambdaQueryWrapper<>();
        if (dto.getStatus() != null) {
            wrapper.eq(MallDistributor::getStatus, dto.getStatus());
        }
        wrapper.orderByDesc(MallDistributor::getId);
        distributorService.page(page, wrapper);
        return Result.ok(page);
    }

    @Operation(summary = "根据ID查询分销员")
    @GetMapping("/{id}")
    public Result<MallDistributor> getById(@PathVariable("id") Long id) {
        return Result.ok(distributorService.getById(id));
    }

    @Operation(summary = "审核：通过/驳回")
    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody DistributorAuditDTO dto) {
        if (dto == null || dto.getId() == null || dto.getPass() == null) {
            return Result.fail(400, "参数错误：id/pass 必填");
        }
        distributorService.audit(dto.getId(), dto.getPass(), dto.getRejectReason());
        return Result.ok();
    }

    @Operation(summary = "启用分销员")
    @PostMapping("/enable/{id}")
    public Result<Void> enable(@PathVariable("id") Long id) {
        distributorService.enable(id);
        return Result.ok();
    }

    @Operation(summary = "停用分销员")
    @PostMapping("/disable/{id}")
    public Result<Void> disable(@PathVariable("id") Long id) {
        distributorService.disable(id);
        return Result.ok();
    }

    @Data
    public static class DistributorAuditDTO {
        private Long id;
        private Boolean pass;
        private String rejectReason;
    }
}
