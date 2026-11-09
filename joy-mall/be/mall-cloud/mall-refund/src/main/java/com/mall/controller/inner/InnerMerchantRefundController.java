package com.mall.controller.inner;

import com.mall.common.exception.BusinessException;
import com.mall.entity.MallRefund;
import com.mall.service.MallRefundService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 售后域商家内部端点（V11，{@code /inner/**} 免鉴权）。
 *
 * <p>售后单表没有 merchant_id，本店范围靠 {@code mall_refund JOIN mall_order.merchant_id} 圈定；
 * 审核直接调 {@link MallRefundService#audit}，状态机（1→2/3）与平台后台完全一致。
 */
@RestController
@RequestMapping("/inner/merchant")
public class InnerMerchantRefundController {

    private final MallRefundService refundService;
    private final JdbcTemplate jdbcTemplate;

    public InnerMerchantRefundController(MallRefundService refundService, JdbcTemplate jdbcTemplate) {
        this.refundService = refundService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/refund/page")
    public Map<String, Object> page(@RequestParam("merchantId") Long merchantId,
                                    @RequestParam(value = "keyword", required = false) String keyword,
                                    @RequestParam(value = "status", required = false) Integer status,
                                    @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                    @RequestParam(value = "pageSize", defaultValue = "10") long pageSize) {
        requireMerchant(merchantId);
        StringBuilder where = new StringBuilder(" FROM mall_refund r JOIN mall_order o ON o.id = r.order_id"
                + " WHERE o.merchant_id = ? AND r.deleted = 0 AND o.deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(merchantId);
        if (status != null) {
            where.append(" AND r.status = ?");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (r.refund_no LIKE ? OR r.order_no LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        long size = Math.min(Math.max(pageSize, 1), 500);
        long offset = Math.max(pageNum - 1, 0) * size;
        args.add(size);
        args.add(offset);
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT r.id" + where + " ORDER BY r.id DESC LIMIT ? OFFSET ?", Long.class, args.toArray());
        List<MallRefund> records = ids.isEmpty() ? List.of() : refundService.listByIds(ids);
        Map<Long, MallRefund> byId = new LinkedHashMap<>();
        records.forEach(r -> byId.put(r.getId(), r));
        List<MallRefund> ordered = ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", ordered);
        data.put("total", total == null ? 0L : total);
        data.put("pageNum", pageNum);
        data.put("pageSize", size);
        return data;
    }

    /** 售后审核：agree=true 同意（→2），false 拒绝（→3，必须给意见） */
    @PostMapping("/refund/audit")
    public Map<String, Object> audit(@RequestParam("merchantId") Long merchantId,
                                     @RequestParam("id") Long id,
                                     @RequestParam("agree") boolean agree,
                                     @RequestParam(value = "auditRemark", required = false) String auditRemark,
                                     @RequestParam(value = "operator", required = false) String operator) {
        requireMerchant(merchantId);
        MallRefund refund = refundService.getById(id);
        if (refund == null) {
            throw new BusinessException(400, "售后单不存在");
        }
        Long owner = orderMerchantId(refund.getOrderId());
        if (owner == null || !owner.equals(merchantId)) {
            throw new BusinessException(403, "无权审核其他店铺的售后单");
        }
        refundService.audit(id, agree, auditRemark,
                operator == null || operator.isBlank() ? "merchant" : operator);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("status", agree ? 2 : 3);
        return data;
    }

    private Long orderMerchantId(Long orderId) {
        if (orderId == null) {
            return null;
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT merchant_id FROM mall_order WHERE id = ? AND deleted = 0", Long.class, orderId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void requireMerchant(Long merchantId) {
        if (merchantId == null) {
            throw new BusinessException(400, "参数错误：merchantId 必填");
        }
    }
}
