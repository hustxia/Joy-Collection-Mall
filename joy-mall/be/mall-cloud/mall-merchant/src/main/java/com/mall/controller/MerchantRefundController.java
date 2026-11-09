package com.mall.controller;

import com.mall.common.context.MerchantContext;
import com.mall.common.exception.BusinessException;
import com.mall.common.log.Log;
import com.mall.common.result.Result;
import com.mall.merchant.feign.MerchantRefundClient;
import com.mall.merchant.support.MerchantGuard;
import com.mall.merchant.support.MerchantResults;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 商家售后自助（V11）：只看本店订单产生的售后单，审核复用售后域状态机。
 *
 * <p>售后单表没有 merchant_id，归属只能经订单判定：先查售后单的 order_id，
 * 再校验该订单是否属于当前商家——越权探测在这里就会被拦下并留痕。
 */
@Tag(name = "商家后台-售后", description = "商家售后查询与审核")
@RestController
@RequestMapping("/merchant-admin/refund")
public class MerchantRefundController {

    private final MerchantRefundClient refundClient;
    private final MerchantGuard guard;
    private final JdbcTemplate jdbcTemplate;

    public MerchantRefundController(MerchantRefundClient refundClient, MerchantGuard guard,
                                    JdbcTemplate jdbcTemplate) {
        this.refundClient = refundClient;
        this.guard = guard;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Operation(summary = "本店售后分页（单号/订单号模糊，状态可选）")
    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "status", required = false) Integer status,
                                            @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        Long merchantId = guard.currentMerchantId();
        return Result.ok(MerchantResults.unwrap(
                refundClient.page(merchantId, keyword, status, pageNum, pageSize)));
    }

    @Operation(summary = "售后审核：agree=true 同意 → 已同意；false 拒绝 → 已拒绝")
    @Log(title = "商家售后", type = "UPDATE")
    @PostMapping("/audit")
    public Result<Map<String, Object>> audit(@RequestBody AuditDTO dto) {
        if (dto == null || dto.getId() == null || dto.getAgree() == null) {
            throw new BusinessException(400, "参数错误：id 与 agree 必填");
        }
        guard.currentMerchantId();
        List<Long> orderIds = jdbcTemplate.queryForList(
                "SELECT order_id FROM mall_refund WHERE id = ? AND deleted = 0", Long.class, dto.getId());
        if (orderIds.isEmpty()) {
            throw new BusinessException(400, "售后单不存在");
        }
        if (!guard.assertOwned("mall_order", orderIds.get(0), "售后审核")) {
            throw new BusinessException(400, "售后单所属订单不存在");
        }
        return Result.ok(MerchantResults.unwrap(refundClient.audit(
                MerchantContext.get(), dto.getId(), Boolean.TRUE.equals(dto.getAgree()),
                dto.getAuditRemark(), MerchantContext.getUsername())));
    }

    /** 审核入参 */
    public static class AuditDTO {
        private Long id;
        private Boolean agree;
        private String auditRemark;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Boolean getAgree() {
            return agree;
        }

        public void setAgree(Boolean agree) {
            this.agree = agree;
        }

        public String getAuditRemark() {
            return auditRemark;
        }

        public void setAuditRemark(String auditRemark) {
            this.auditRemark = auditRemark;
        }
    }
}
