package com.mall.merchant.support;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mall.common.context.MerchantContext;
import com.mall.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 商家越权守卫（V11）：归属校验 + 403 留痕。
 *
 * <p>拦截器（{@code @MerchantScoped}）负责「查不到别人家的行」，这里负责
 * 「查不到时要能说清楚是越权还是不存在」：显式探一次归属列，
 * 命中了别家就写 {@code sys_oper_log} 并抛 403，避免越权探测被伪装成 404 而无声无息。
 *
 * <p>日志写失败绝不影响主流程（与 LogAspect 同口径）——留痕是辅助，拦截才是目的。
 */
@Slf4j
@Component
public class MerchantGuard {

    /**
     * 归属探查支持的资源表（只允许这几个，避免拼接任意表名）。
     * <p>售后单没有 merchant_id 列，归属要经订单判定（见 MerchantRefundService），故不在此列。
     */
    private static final List<String> TABLES = List.of(
            "mall_goods", "mall_order", "mall_merchant_settlement", "mall_merchant_withdrawal");

    private final JdbcTemplate jdbcTemplate;

    public MerchantGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 当前登录商家 ID；没有商家上下文直接 403（防内部调用误用商家接口） */
    public Long currentMerchantId() {
        Long merchantId = MerchantContext.get();
        if (merchantId == null) {
            throw new BusinessException(403, "缺少商家身份，请重新登录商家后台");
        }
        return merchantId;
    }

    /**
     * 校验资源属于当前商家。
     *
     * @param table  资源表（必须在白名单内）
     * @param id     资源 ID
     * @param action 业务动作，写日志用（如「查看商品」）
     * @return 记录不存在时返回 false（由调用方给 400）；归属正确返回 true
     */
    public boolean assertOwned(String table, Long id, String action) {
        if (id == null) {
            throw new BusinessException(400, "参数错误：id 必填");
        }
        if (!TABLES.contains(table)) {
            throw new IllegalArgumentException("未登记的隔离表：" + table);
        }
        List<Long> owners = jdbcTemplate.queryForList(
                "SELECT merchant_id FROM " + table + " WHERE id = ? AND deleted = 0", Long.class, id);
        if (owners.isEmpty()) {
            return false;
        }
        Long owner = owners.get(0);
        Long current = currentMerchantId();
        if (owner == null || !owner.equals(current)) {
            deny(action, table + "#" + id + " 归属商家 " + owner + "，当前商家 " + current);
        }
        return true;
    }

    /**
     * SKU 归属校验：SKU 本身没有 merchant_id，归属看它挂在哪个商品上。
     *
     * @return SKU 或所属商品不存在时返回 false
     */
    public boolean assertSkuOwned(Long skuId, String action) {
        if (skuId == null) {
            throw new BusinessException(400, "参数错误：skuId 必填");
        }
        List<Long> owners = jdbcTemplate.queryForList(
                "SELECT g.merchant_id FROM mall_sku s JOIN mall_goods g ON g.id = s.goods_id "
                        + "WHERE s.id = ? AND s.deleted = 0 AND g.deleted = 0", Long.class, skuId);
        if (owners.isEmpty()) {
            return false;
        }
        Long owner = owners.get(0);
        Long current = currentMerchantId();
        if (owner == null || !owner.equals(current)) {
            deny(action, "mall_sku#" + skuId + " 归属商家 " + owner + "，当前商家 " + current);
        }
        return true;
    }

    /** 越权：先留痕再抛 403 */
    public void deny(String action, String detail) {        String uri = null;
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                uri = attributes.getRequest().getRequestURI();
            }
        } catch (Exception ignored) {
            // 非 Web 线程（定时任务等）没有请求上下文，留痕时 uri 为空即可
        }
        log.warn("商家越权访问被拦截：action={}, detail={}, merchantId={}", action, detail, MerchantContext.get());
        try {
            // 列名以 sys_oper_log 实际结构为准：该表没有 oper_time，多写一列会让整条 INSERT 失败，
            // 而失败被 catch 吞掉 → 越权全部「静默放过、事后无痕」。留痕 SQL 必须跟着表结构走。
            jdbcTemplate.update("INSERT INTO sys_oper_log (id, title, oper_type, method, request_uri, request_method,"
                            + " oper_param, status, error_msg, oper_user_id, oper_name, cost_ms,"
                            + " create_time, update_time, deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?, NOW(), NOW(), 0)",
                    IdWorker.getId(), action, "FORBIDDEN", "MerchantGuard.deny", uri, null,
                    detail, 0, "商家越权访问：" + detail,
                    MerchantContext.getMerchantUserId(), MerchantContext.getUsername(),
                    0L);
        } catch (Exception e) {
            log.warn("越权日志写入失败（不影响拦截）：{}", e.getMessage());
        }
        throw new BusinessException(403, "无权访问其他店铺的数据");
    }
}
