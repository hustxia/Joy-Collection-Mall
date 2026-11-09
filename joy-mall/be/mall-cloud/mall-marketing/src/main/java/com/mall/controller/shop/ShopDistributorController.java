package com.mall.controller.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.context.UserContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.result.Result;
import com.mall.entity.MallCommissionRecord;
import com.mall.entity.MallDistributor;
import com.mall.entity.MallInvitation;
import com.mall.service.MallCommissionRecordService;
import com.mall.service.MallDistributorService;
import com.mall.service.MallInvitationService;
import com.mall.service.MallWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端分销中心（V9）：申请/绑定/我的状态/团队/佣金明细/提现
 */
@Tag(name = "商城前台-分销中心", description = "分销员申请/邀请/佣金/提现")
@RestController
@RequestMapping("/shop/distributor")
public class ShopDistributorController {

    private final MallDistributorService distributorService;
    private final MallInvitationService invitationService;
    private final MallCommissionRecordService commissionRecordService;
    private final MallWithdrawalService withdrawalService;

    public ShopDistributorController(MallDistributorService distributorService,
                                    MallInvitationService invitationService,
                                    MallCommissionRecordService commissionRecordService,
                                    MallWithdrawalService withdrawalService) {
        this.distributorService = distributorService;
        this.invitationService = invitationService;
        this.commissionRecordService = commissionRecordService;
        this.withdrawalService = withdrawalService;
    }

    @Operation(summary = "我的分销状态：含邀请码/状态/累计与可提现佣金")
    @GetMapping("/info")
    public Result<MallDistributor> info() {
        Long memberId = UserContext.getUserId();
        return Result.ok(distributorService.getByMember(memberId));
    }

    @Operation(summary = "申请成为分销员，返回专属邀请码")
    @PostMapping("/apply")
    public Result<Map<String, Object>> apply() {
        Long memberId = UserContext.getUserId();
        String code = distributorService.apply(memberId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inviteCode", code);
        return Result.ok(data);
    }

    @Operation(summary = "绑定邀请关系：通过上级邀请码绑定（防自邀/防篡改）")
    @PostMapping("/bind")
    public Result<Void> bind(@RequestBody BindDTO dto) {
        Long memberId = UserContext.getUserId();
        distributorService.bind(memberId, dto.getInviteCode(), dto.getChannel());
        return Result.ok();
    }

    @Operation(summary = "我的团队：我邀请的下级会员")
    @GetMapping("/team")
    public Result<List<MallInvitation>> team() {
        Long memberId = UserContext.getUserId();
        MallDistributor d = distributorService.getByMember(memberId);
        if (d == null) {
            return Result.ok(List.of());
        }
        List<MallInvitation> list = invitationService.list(new LambdaQueryWrapper<MallInvitation>()
                .eq(MallInvitation::getInviterId, d.getId())
                .eq(MallInvitation::getDeleted, 0)
                .orderByDesc(MallInvitation::getId));
        return Result.ok(list);
    }

    @Operation(summary = "我的佣金明细")
    @GetMapping("/commission")
    public Result<Page<MallCommissionRecord>> commission(@RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "20") int pageSize) {
        Long memberId = UserContext.getUserId();
        MallDistributor d = distributorService.getByMember(memberId);
        Page<MallCommissionRecord> page = new Page<>(pageNum, pageSize);
        if (d != null) {
            commissionRecordService.page(page, new LambdaQueryWrapper<MallCommissionRecord>()
                    .eq(MallCommissionRecord::getDistributorId, d.getId())
                    .orderByDesc(MallCommissionRecord::getId));
        }
        return Result.ok(page);
    }

    @Operation(summary = "佣金提现申请")
    @PostMapping("/withdraw")
    public Result<Void> withdraw(@RequestBody WithdrawDTO dto) {
        Long memberId = UserContext.getUserId();
        MallDistributor d = distributorService.getByMember(memberId);
        if (d == null) {
            throw new BusinessException(400, "尚未成为分销员");
        }
        withdrawalService.apply(d.getId(), dto.getAmount(), dto.getMethod());
        return Result.ok();
    }

    @Data
    public static class BindDTO {
        private String inviteCode;
        private String channel;
    }

    @Data
    public static class WithdrawDTO {
        private Long amount;
        private Integer method;
    }
}
