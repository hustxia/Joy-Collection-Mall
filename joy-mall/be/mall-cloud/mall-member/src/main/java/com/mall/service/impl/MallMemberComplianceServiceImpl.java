package com.mall.service.impl;

import com.mall.common.exception.BusinessException;
import com.mall.entity.MallMember;
import com.mall.mapper.MallMemberMapper;
import com.mall.service.MallMemberComplianceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会员合规实现（V11）。
 *
 * <p>注销的三个阶段：
 * <ol>
 *   <li>申请：状态 0→1，记生效时间 = 现在 + 7 天；此时账号仍可用，登录会带「冷静期」提示；</li>
 *   <li>撤销：状态 1→0，清空时间戳；</li>
 *   <li>到期执行：状态 1→2，phone/nickname/avatar 匿名化，member_id 与历史订单原样保留。</li>
 * </ol>
 *
 * <p>到期执行走<b>惰性 + 批量双入口</b>：登录/查询状态时顺带判一次（无需调度器也能生效），
 * 同时提供 {@link #executeDue()} 给定时任务或运维脚本调用。
 */
@Slf4j
@Service
public class MallMemberComplianceServiceImpl implements MallMemberComplianceService {

    /** 注销后的手机号占位前缀：不含数字段，任何「按手机号登录」都必然查不到人 */
    /**
     * 注销后的手机号占位：固定 11 位、以 C 开头（C = cancelled，一眼能认出不是手机号）。
     * <p>不能用「前缀 + 雪花 ID」——19 位 ID 加前缀有 29 个字符，超出 phone 列宽（测试库 varchar(11)、
     * 生产 varchar(20)），注销执行会直接 500；而 9 开头不满足 ^1\d{10}$ 的注册规则，
     * 所以这个号码永远不可能被真实用户注册到，也不影响唯一键。
     */
    private static final long PLACEHOLDER_PHONE_MOD = 10_000_000_000L;
    /** 注销后的昵称占位 */
    private static final String CANCELLED_NICKNAME = "已注销用户";

    private final MallMemberMapper memberMapper;
    private final JdbcTemplate jdbcTemplate;

    public MallMemberComplianceServiceImpl(MallMemberMapper memberMapper, JdbcTemplate jdbcTemplate) {
        this.memberMapper = memberMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applyCancel(Long memberId) {
        MallMember member = requireMember(memberId);
        Integer status = member.getCancelStatus() == null ? STATUS_NORMAL : member.getCancelStatus();
        if (status == STATUS_CANCELLED) {
            throw new BusinessException(400, "账号已注销，无需重复申请");
        }
        if (status == STATUS_COOLING) {
            throw new BusinessException(400, "账号已在注销冷静期，可直接撤销");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effective = now.plusDays(COOLING_DAYS);
        jdbcTemplate.update("UPDATE mall_member SET cancel_status = ?, cancel_apply_time = ?,"
                        + " cancel_effective_time = ? WHERE id = ?",
                STATUS_COOLING, Timestamp.valueOf(now), Timestamp.valueOf(effective), memberId);
        log.info("会员申请注销：memberId={}, 生效时间={}", memberId, effective);
        return statusData(STATUS_COOLING, now, effective);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> revokeCancel(Long memberId) {
        MallMember member = requireMember(memberId);
        Integer status = member.getCancelStatus() == null ? STATUS_NORMAL : member.getCancelStatus();
        if (status == STATUS_CANCELLED) {
            throw new BusinessException(400, "账号已注销，无法撤销");
        }
        if (status != STATUS_COOLING) {
            throw new BusinessException(400, "账号不在注销冷静期");
        }
        // 超期未执行的也允许撤销吗？不允许：到期即视为注销已生效，避免「早该注销」的账号被复活
        if (member.getCancelEffectiveTime() != null
                && LocalDateTime.now().isAfter(member.getCancelEffectiveTime())) {
            executeIfDue(memberId);
            throw new BusinessException(400, "注销冷静期已过，账号已注销，无法撤销");
        }
        jdbcTemplate.update("UPDATE mall_member SET cancel_status = ?, cancel_apply_time = NULL,"
                + " cancel_effective_time = NULL WHERE id = ? AND cancel_status = ?", STATUS_NORMAL, memberId,
                STATUS_COOLING);
        log.info("会员撤销注销：memberId={}", memberId);
        return statusData(STATUS_NORMAL, null, null);
    }

    @Override
    public Map<String, Object> cancelStatus(Long memberId) {
        MallMember member = requireMember(memberId);
        Integer status = member.getCancelStatus() == null ? STATUS_NORMAL : member.getCancelStatus();
        if (status == STATUS_COOLING) {
            executeIfDue(memberId);
            member = requireMember(memberId);
            status = member.getCancelStatus() == null ? STATUS_NORMAL : member.getCancelStatus();
        }
        return statusData(status, member.getCancelApplyTime(), member.getCancelEffectiveTime());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean executeIfDue(Long memberId) {
        MallMember member = memberMapper.selectById(memberId);
        if (member == null) {
            return false;
        }
        Integer status = member.getCancelStatus() == null ? STATUS_NORMAL : member.getCancelStatus();
        if (status != STATUS_COOLING) {
            return false;
        }
        if (member.getCancelEffectiveTime() == null || LocalDateTime.now().isBefore(member.getCancelEffectiveTime())) {
            return false;
        }
        anonymize(member);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int executeDue() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM mall_member WHERE cancel_status = ? AND cancel_effective_time IS NOT NULL"
                        + " AND cancel_effective_time <= NOW() AND deleted = 0", Long.class, STATUS_COOLING);
        int done = 0;
        for (Long id : ids) {
            MallMember member = memberMapper.selectById(id);
            if (member != null) {
                anonymize(member);
                done++;
            }
        }
        if (done > 0) {
            log.info("注销到期批量执行完成：{} 个账号", done);
        }
        return done;
    }

    /**
     * 脱敏：只动 PII 三件套，member_id 与订单/售后/资金流水全部保留。
     * 同时清掉该会员所有会话——注销后必须立刻掉线，不能等 token 自然过期。
     */
    private void anonymize(MallMember member) {
        String placeholder = "C" + String.format("%010d", Math.abs(member.getId()) % PLACEHOLDER_PHONE_MOD);
        int affected = jdbcTemplate.update("UPDATE mall_member SET phone = ?, nickname = ?, avatar = NULL,"
                        + " cancel_status = ?, cancel_effective_time = IFNULL(cancel_effective_time, NOW())"
                        + " WHERE id = ? AND cancel_status = ?",
                placeholder, CANCELLED_NICKNAME, STATUS_CANCELLED, member.getId(), STATUS_COOLING);
        if (affected == 0) {
            // 并发下另一次执行已经处理过，直接返回，不重复写
            return;
        }
        jdbcTemplate.update("DELETE FROM sys_session WHERE username = ?", "m:" + member.getId());
        log.info("会员注销已执行并脱敏：memberId={}", member.getId());
    }

    @Override
    public Map<String, Object> export(Long memberId) {
        MallMember member = requireMember(memberId);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", String.valueOf(member.getId()));
        profile.put("nickname", member.getNickname());
        profile.put("phone", member.getPhone());
        profile.put("avatar", member.getAvatar());
        profile.put("gender", member.getGender());
        profile.put("points", member.getPoints());
        profile.put("source", member.getSource());
        profile.put("status", member.getStatus());
        profile.put("createTime", member.getCreateTime());

        List<Map<String, Object>> addresses = jdbcTemplate.queryForList(
                "SELECT receiver_name, receiver_phone, province, city, district, detail, is_default"
                        + " FROM mall_address WHERE member_id = ? AND deleted = 0 ORDER BY id", memberId);
        // 订单只导出定位与金额，不含收件人快照之外的他人信息；条件里写死 member_id 即隔离边界
        List<Map<String, Object>> orders = jdbcTemplate.queryForList(
                "SELECT order_no, status, pay_status, total_amount, pay_amount, create_time"
                        + " FROM mall_order WHERE member_id = ? AND deleted = 0 ORDER BY id DESC", memberId);
        List<Map<String, Object>> coupons = jdbcTemplate.queryForList(
                "SELECT coupon_id, order_id, status, received_time, used_time"
                        + " FROM mall_coupon_record WHERE member_id = ? AND deleted = 0 ORDER BY id DESC", memberId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportTime", LocalDateTime.now());
        data.put("profile", profile);
        data.put("addresses", addresses);
        data.put("orders", orders);
        data.put("coupons", coupons);
        data.put("summary", Map.of("addressCount", addresses.size(),
                "orderCount", orders.size(), "couponCount", coupons.size()));
        return data;
    }

    @Override
    public String exportCsv(Long memberId) {
        Map<String, Object> data = export(memberId);
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("类别,字段,值\r\n");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) data.get("profile");
        for (Map.Entry<String, Object> entry : profile.entrySet()) {
            sb.append("基本资料,").append(escape(entry.getKey())).append(',')
                    .append(escape(text(entry.getValue()))).append("\r\n");
        }
        appendRows(sb, "收货地址", (List<?>) data.get("addresses"));
        appendRows(sb, "订单", (List<?>) data.get("orders"));
        appendRows(sb, "优惠券", (List<?>) data.get("coupons"));
        return sb.toString();
    }

    private void appendRows(StringBuilder sb, String section, List<?> rows) {
        if (rows == null) {
            return;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                cells.add(String.valueOf(entry.getKey()) + "=" + text(entry.getValue()));
            }
            sb.append(section).append(',').append(escape(String.join("; ", cells))).append(",\r\n");
        }
    }

    private Map<String, Object> statusData(int status, LocalDateTime applyTime, LocalDateTime effectiveTime) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cancelStatus", status);
        data.put("cancelStatusText", switch (status) {
            case STATUS_COOLING -> "注销冷静期";
            case STATUS_CANCELLED -> "已注销";
            default -> "正常";
        });
        data.put("applyTime", applyTime);
        data.put("effectiveTime", effectiveTime);
        if (status == STATUS_COOLING && effectiveTime != null) {
            long days = Math.max(Duration.between(LocalDateTime.now(), effectiveTime).toDays(), 0);
            data.put("remainDays", days);
        }
        return data;
    }

    private MallMember requireMember(Long memberId) {
        MallMember member = memberId == null ? null : memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException(401, "会员不存在，请重新登录");
        }
        return member;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }
}
