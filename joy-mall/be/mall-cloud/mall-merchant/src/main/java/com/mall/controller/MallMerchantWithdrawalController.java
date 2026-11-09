package com.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.dto.MallMerchantWithdrawalPageDTO;
import com.mall.dto.WithdrawalApplyDTO;
import com.mall.dto.WithdrawalAuditDTO;
import com.mall.entity.MallMerchantWithdrawal;
import com.mall.service.MallMerchantWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 商家提现后台（V10）：代商家发起提现、审核打款/拒绝。
 */
@Tag(name = "商家提现", description = "提现申请与审核")
@RestController
@RequestMapping("/mall_merchant_withdrawal")
public class MallMerchantWithdrawalController {

    private final MallMerchantWithdrawalService withdrawalService;

    public MallMerchantWithdrawalController(MallMerchantWithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @Operation(summary = "分页查询提现单")
    @PostMapping("/page")
    public Result<Page<MallMerchantWithdrawal>> page(@RequestBody MallMerchantWithdrawalPageDTO dto) {
        return Result.ok(withdrawalService.pageQuery(dto));
    }

    @Operation(summary = "发起提现（本期由平台代商家操作，申请即冻结余额）")
    @PostMapping("/apply")
    public Result<MallMerchantWithdrawal> apply(@RequestBody WithdrawalApplyDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, "参数错误");
        }
        return Result.ok(withdrawalService.apply(dto.getMerchantId(), dto.getAmount(), dto.getMethod()));
    }

    @Operation(summary = "提现审核：通过=已打款，拒绝=余额原路退回")
    @PostMapping("/audit")
    public Result<Void> audit(@RequestBody WithdrawalAuditDTO dto) {
        if (dto == null || dto.getId() == null || dto.getPass() == null) {
            throw new BusinessException(400, "参数错误：id 与 pass 必填");
        }
        String operator = UserContext.getUsername() == null ? "system" : UserContext.getUsername();
        withdrawalService.audit(dto.getId(), dto.getPass(), dto.getRejectReason(), operator);
        return Result.ok();
    }
}
