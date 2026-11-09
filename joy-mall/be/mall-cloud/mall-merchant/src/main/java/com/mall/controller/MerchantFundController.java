package com.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.common.exception.BusinessException;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.dto.MallMerchantSettlementPageDTO;
import com.mall.dto.MallMerchantWithdrawalPageDTO;
import com.mall.entity.MallMerchantBillDetail;
import com.mall.entity.MallMerchantSettlement;
import com.mall.entity.MallMerchantWithdrawal;
import com.mall.merchant.support.MerchantBillCsv;
import com.mall.merchant.support.MerchantGuard;
import com.mall.service.MallMerchantBillDetailService;
import com.mall.service.MallMerchantSettlementService;
import com.mall.service.MallMerchantWithdrawalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家资金自助（V11）：结算单查询、提现申请、对账单导出。
 *
 * <p>提现与结算的算法一行都没重写：申请走 {@code MallMerchantWithdrawalService.apply}，
 * 它用「余额足够才扣」的条件 UPDATE 保证不会超提；拒绝退回也复用同一套审核逻辑。
 */
@Tag(name = "商家后台-资金", description = "结算单/提现/对账导出")
@RestController
@RequestMapping("/merchant-admin")
public class MerchantFundController {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MallMerchantSettlementService settlementService;
    private final MallMerchantWithdrawalService withdrawalService;
    private final MallMerchantBillDetailService billDetailService;
    private final MerchantGuard guard;

    public MerchantFundController(MallMerchantSettlementService settlementService,
                                  MallMerchantWithdrawalService withdrawalService,
                                  MallMerchantBillDetailService billDetailService,
                                  MerchantGuard guard) {
        this.settlementService = settlementService;
        this.withdrawalService = withdrawalService;
        this.billDetailService = billDetailService;
        this.guard = guard;
    }

    @Operation(summary = "本店结算单分页")
    @GetMapping("/settlement/page")
    public Result<Page<MallMerchantSettlement>> settlementPage(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        MallMerchantSettlementPageDTO dto = new MallMerchantSettlementPageDTO();
        dto.setMerchantId(guard.currentMerchantId());
        dto.setStatus(status);
        dto.setPageNum((int) pageNum);
        dto.setPageSize((int) pageSize);
        return Result.ok(settlementService.pageQuery(dto));
    }

    @Operation(summary = "本店提现单分页")
    @GetMapping("/withdrawal/page")
    public Result<Page<MallMerchantWithdrawal>> withdrawalPage(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        MallMerchantWithdrawalPageDTO dto = new MallMerchantWithdrawalPageDTO();
        dto.setMerchantId(guard.currentMerchantId());
        dto.setStatus(status);
        dto.setPageNum((int) pageNum);
        dto.setPageSize((int) pageSize);
        return Result.ok(withdrawalService.pageQuery(dto));
    }

    @Operation(summary = "提现申请：申请即冻结余额（余额不足 400）")
    @Log(title = "商家提现", type = "INSERT")
    @PostMapping("/withdrawal/apply")
    public Result<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        Long merchantId = guard.currentMerchantId();
        Long amount = asLong(body == null ? null : body.get("amount"));
        Integer method = asInt(body == null ? null : body.get("method"));
        MallMerchantWithdrawal withdrawal = withdrawalService.apply(merchantId, amount, method);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", String.valueOf(withdrawal.getId()));
        data.put("amount", withdrawal.getAmount());
        data.put("status", withdrawal.getStatus());
        return Result.ok(data);
    }

    @Operation(summary = "对账单导出（CSV，UTF-8 BOM，Excel 可直接打开）")
    @GetMapping("/bill/export")
    public ResponseEntity<byte[]> exportBill(
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "endTime", required = false) String endTime) {
        Long merchantId = guard.currentMerchantId();
        LambdaQueryWrapper<MallMerchantBillDetail> wrapper = new LambdaQueryWrapper<MallMerchantBillDetail>()
                .eq(MallMerchantBillDetail::getMerchantId, merchantId);
        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);
        if (start != null) {
            wrapper.ge(MallMerchantBillDetail::getCreateTime, start);
        }
        if (end != null) {
            wrapper.le(MallMerchantBillDetail::getCreateTime, end);
        }
        wrapper.orderByDesc(MallMerchantBillDetail::getId);
        List<MallMerchantBillDetail> details = billDetailService.list(wrapper);
        List<Map<String, Object>> rows = new ArrayList<>(details.size());
        for (MallMerchantBillDetail d : details) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderNo", d.getOrderNo());
            row.put("settlementId", d.getSettlementId());
            row.put("payAmount", d.getPayAmount());
            row.put("platformFee", d.getPlatformFee());
            row.put("goodsAmount", d.getGoodsAmount());
            row.put("refundAmount", d.getRefundAmount());
            row.put("refundFlag", d.getRefundFlag());
            row.put("createTime", d.getCreateTime());
            rows.add(row);
        }
        byte[] bytes = MerchantBillCsv.build(rows).getBytes(StandardCharsets.UTF_8);
        String fileName = "merchant-bill-" + merchantId + "-" + FILE_TS.format(LocalDateTime.now()) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized);
        } catch (Exception e) {
            throw new BusinessException(400, "时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asInt(Object value) {
        Long v = asLong(value);
        return v == null ? null : v.intValue();
    }
}
