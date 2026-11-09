package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.dto.MallMerchantApplyDTO;
import com.mall.dto.MallMerchantPageDTO;
import com.mall.dto.MallMerchantSaveDTO;
import com.mall.dto.MerchantAuditDTO;
import com.mall.dto.MerchantStatusDTO;
import com.mall.entity.MallMerchant;
import com.mall.entity.MallMerchantAuditLog;
import com.mall.service.MallMerchantAuditLogService;
import com.mall.service.MallMerchantService;
import com.mall.service.impl.MallMerchantServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家入驻后台（V10）：列表 / 详情 / 申请 / 审核 / 启停 / 资料维护。
 */
@Tag(name = "商家入驻", description = "商家审核与店铺管理")
@RestController
@RequestMapping("/mall_merchant")
public class MallMerchantController {

    private final MallMerchantService merchantService;
    private final MallMerchantAuditLogService auditLogService;

    public MallMerchantController(MallMerchantService merchantService,
                                  MallMerchantAuditLogService auditLogService) {
        this.merchantService = merchantService;
        this.auditLogService = auditLogService;
    }

    @Operation(summary = "分页查询商家（关键字/状态/类目）")
    @PostMapping("/page")
    public Result<Page<MallMerchant>> page(@RequestBody MallMerchantPageDTO dto) {
        return Result.ok(merchantService.pageQuery(dto));
    }

    @Operation(summary = "启用店铺下拉（结算/发品选择商家用）")
    @GetMapping("/options")
    public Result<List<Map<String, Object>>> options() {
        List<MallMerchant> list = merchantService.list(new LambdaQueryWrapper<MallMerchant>()
                .eq(MallMerchant::getStatus, MallMerchantServiceImpl.ST_ENABLED)
                .orderByAsc(MallMerchant::getId));
        return Result.ok(list.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", String.valueOf(m.getId()));
            row.put("name", m.getName());
            row.put("merchantNo", m.getMerchantNo());
            return row;
        }).toList());
    }

    @Operation(summary = "商家详情（含审核流水时间线）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long id) {
        MallMerchant merchant = merchantService.getById(id);
        if (merchant == null) {
            throw new BusinessException(400, "商家不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchant", merchant);
        data.put("logs", auditLogService.listByMerchant(id).stream().map(l -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("action", l.getAction());
            row.put("actionText", MallMerchantAuditLog.actionText(l.getAction()));
            row.put("operator", l.getOperator());
            row.put("remark", l.getRemark());
            row.put("createTime", l.getCreateTime());
            return row;
        }).toList());
        return Result.ok(data);
    }

    @Operation(summary = "提交入驻申请（平台代提交）")
    @PostMapping("/apply")
    public Result<MallMerchant> apply(@RequestBody MallMerchantApplyDTO dto) {
        return Result.ok(merchantService.apply(dto, operator()));
    }

    @Operation(summary = "入驻审核：通过 / 驳回（驳回必须给原因）")
    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody MerchantAuditDTO dto) {
        if (dto == null || dto.getId() == null || dto.getPass() == null) {
            throw new BusinessException(400, "参数错误：id 与 pass 必填");
        }
        merchantService.audit(dto.getId(), dto.getPass(), dto.getRejectReason(), operator());
        return Result.ok();
    }

    @Operation(summary = "店铺启用 / 停用")
    @PostMapping("/status")
    public Result<Void> status(@RequestBody MerchantStatusDTO dto) {
        if (dto == null || dto.getId() == null || dto.getEnable() == null) {
            throw new BusinessException(400, "参数错误：id 与 enable 必填");
        }
        merchantService.changeStatus(dto.getId(), dto.getEnable(), operator());
        return Result.ok();
    }

    @Operation(summary = "修改店铺资料与抽成费率")
    @PutMapping
    public Result<Void> update(@RequestBody MallMerchantSaveDTO dto) {
        merchantService.updateProfile(dto);
        return Result.ok();
    }

    private String operator() {
        String username = UserContext.getUsername();
        return username == null ? "system" : username;
    }
}
