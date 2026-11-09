package com.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.dto.MallMerchantSettlementPageDTO;
import com.mall.dto.SettlementBuildDTO;
import com.mall.entity.MallMerchantBillDetail;
import com.mall.entity.MallMerchantSettlement;
import com.mall.service.MallMerchantSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家结算单后台（V10）：分页、手动/周期生成、明细下钻。
 */
@Tag(name = "商家结算", description = "结算单与明细")
@RestController
@RequestMapping("/mall_merchant_settlement")
public class MallMerchantSettlementController {

    private final MallMerchantSettlementService settlementService;

    public MallMerchantSettlementController(MallMerchantSettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @Operation(summary = "分页查询结算单")
    @PostMapping("/page")
    public Result<Page<MallMerchantSettlement>> page(@RequestBody MallMerchantSettlementPageDTO dto) {
        return Result.ok(settlementService.pageQuery(dto));
    }

    @Operation(summary = "生成结算单：归集该商家待出账明细（无则 400）")
    @PostMapping("/build")
    public Result<MallMerchantSettlement> build(@RequestBody SettlementBuildDTO dto) {
        if (dto == null || dto.getMerchantId() == null) {
            throw new BusinessException(400, "参数错误：merchantId 必填");
        }
        String operator = UserContext.getUsername() == null ? "system" : UserContext.getUsername();
        return Result.ok(settlementService.build(dto.getMerchantId(),
                dto.getPeriodStart(), dto.getPeriodEnd(), operator));
    }

    @Operation(summary = "结算单明细")
    @GetMapping("/{id}/details")
    public Result<List<MallMerchantBillDetail>> details(@PathVariable("id") Long id) {
        return Result.ok(settlementService.details(id));
    }
}
