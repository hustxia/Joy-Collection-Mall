package com.mall.common.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录令牌存储：/auth/login 签发，AuthContextFilter 校验。
 * V6.4：签发写 sys_session 落盘，校验先查内存缓存（TTL 60s）miss 回源查库——
 * 内存加速、重启不丢；校验命中即滑动续期。登出删行删缓存。
 */
@Component
@Slf4j
public class TokenStore {

    private static final long CACHE_TTL_SECONDS = 60;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 会话时长（小时），yml auth.session-hours 可配 */
    @Value("${auth.session-hours:24}")
    private int sessionHours;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private volatile long lastCleanupAt = 0;

    public String create(String username, String loginIp) {
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expire = LocalDateTime.now().plusHours(sessionHours);
        jdbcTemplate.update("INSERT INTO sys_session (token, username, login_ip, expire_time) VALUES (?, ?, ?, ?)",
                token, username, loginIp, Timestamp.valueOf(expire));
        cache.put(token, new Entry(username, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
        cleanupExpiredIfDue();
        return token;
    }

    public String usernameOf(String token) {
        Entry cached = cache.get(token);
        Instant now = Instant.now();
        if (cached != null && now.isBefore(cached.cachedUntil())) {
            return cached.username();
        }
        // 缓存过期/未命中 → 回源查库；库里有则滑动续期并回填缓存
        var rows = jdbcTemplate.queryForList(
                "SELECT username, expire_time FROM sys_session WHERE token = ? AND deleted = 0",
                token);
        if (rows.isEmpty()) {
            cache.remove(token);
            return null;
        }
        Object expireRaw = rows.get(0).get("expire_time");
        LocalDateTime expire = expireRaw instanceof java.time.LocalDateTime ldt
                ? ldt : ((Timestamp) expireRaw).toLocalDateTime();
        if (LocalDateTime.now().isAfter(expire)) {
            remove(token);
            return null;
        }
        String username = String.valueOf(rows.get(0).get("username"));
        LocalDateTime newExpire = LocalDateTime.now().plusHours(sessionHours);
        jdbcTemplate.update("UPDATE sys_session SET expire_time = ? WHERE token = ?",
                Timestamp.valueOf(newExpire), token);
        cache.put(token, new Entry(username, now.plusSeconds(CACHE_TTL_SECONDS)));
        return username;
    }

    public void remove(String token) {
        cache.remove(token);
        jdbcTemplate.update("DELETE FROM sys_session WHERE token = ?", token);
    }

    /** 惰性清理过期会话：距上次清理超 1 小时才扫一次，不依赖调度器 */
    private void cleanupExpiredIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupAt < 3600_000L) {
            return;
        }
        lastCleanupAt = now;
        try {
            jdbcTemplate.update("DELETE FROM sys_session WHERE expire_time < NOW()");
        } catch (Exception e) {
            // 清理失败不影响登录主链路，下次再试；但要留痕便于排查
            log.warn("过期会话清理失败：{}", e.getMessage());
        }
    }

    private record Entry(String username, Instant cachedUntil) {
    }
}
