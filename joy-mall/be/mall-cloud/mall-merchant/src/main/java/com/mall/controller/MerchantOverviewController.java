package com.mall.controller;

import com.mall.common.result.Result;
import com.mall.entity.MallMerchant;
import com.mall.mapper.MallMerchantMapper;
import com.mall.merchant.support.MerchantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商家经营概览（V11）：本店商品 / 订单 / 售后 / 资金的一屏汇总。
 *
 * <p>只读同库聚合，不跨服务调 Feign：概览是打开页面就要出的数据，
 * 背后挂四五个 RPC 会让首屏受下游抖动牵连。
 */
@Tag(name = "商家后台-概览", description = "本店经营概况")
@RestController
@RequestMapping("/merchant-admin/overview")
public class MerchantOverviewController {

    private final JdbcTemplate jdbcTemplate;
    private final MallMerchantMapper merchantMapper;
    private final MerchantGuard guard;

    public MerchantOverviewController(JdbcTemplate jdbcTemplate, MallMerchantMapper merchantMapper,
                                      MerchantGuard guard) {
        this.jdbcTemplate = jdbcTemplate;
        this.merchantMapper = merchantMapper;
        this.guard = guard;
    }

    @Operation(summary = "本店经营概览")
    @GetMapping
    public Result<Map<String, Object>> overview() {
        Long merchantId = guard.currentMerchantId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("goodsCount", count("SELECT COUNT(*) FROM mall_goods WHERE merchant_id = ? AND deleted = 0", merchantId));
        data.put("onSaleCount", count("SELECT COUNT(*) FROM mall_goods WHERE merchant_id = ? AND status = 1 AND deleted = 0", merchantId));
        data.put("pendingShipCount", count("SELECT COUNT(*) FROM mall_order WHERE merchant_id = ? AND status = 20 AND deleted = 0", merchantId));
        data.put("todayOrderCount", count("SELECT COUNT(*) FROM mall_order WHERE merchant_id = ? AND create_time >= CURDATE() AND deleted = 0", merchantId));
        data.put("todayPayAmount", money("SELECT IFNULL(SUM(pay_amount),0) FROM mall_order WHERE merchant_id = ? AND pay_status = 1 AND create_time >= CURDATE() AND deleted = 0", merchantId));
        data.put("monthPayAmount", money("SELECT IFNULL(SUM(pay_amount),0) FROM mall_order WHERE merchant_id = ? AND pay_status = 1 AND create_time >= DATE_FORMAT(CURDATE(),'%Y-%m-01') AND deleted = 0", merchantId));
        data.put("pendingRefundCount", count("SELECT COUNT(*) FROM mall_refund r JOIN mall_order o ON o.id = r.order_id "
                + "WHERE o.merchant_id = ? AND r.status = 1 AND r.deleted = 0", merchantId));
        data.put("settlementPendingCount", count("SELECT COUNT(*) FROM mall_merchant_settlement WHERE merchant_id = ? AND status = 0 AND deleted = 0", merchantId));
        MallMerchant merchant = merchantMapper.selectById(merchantId);
        data.put("accountBalance", merchant == null ? 0L : merchant.getAccountBalance());
        data.put("settledTotal", merchant == null ? 0L : merchant.getSettledTotal());
        data.put("settleRate", merchant == null ? null : merchant.getSettleRate());
        return Result.ok(data);
    }

    private long count(String sql, Long merchantId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, merchantId);
        return value == null ? 0L : value;
    }

    private long money(String sql, Long merchantId) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, merchantId);
        return value == null ? 0L : value;
    }
}
