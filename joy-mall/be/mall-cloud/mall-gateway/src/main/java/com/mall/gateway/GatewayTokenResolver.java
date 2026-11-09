package com.mall.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关侧令牌解析（V6 S-03）。
 *
 * <p>与各服务内 {@code TokenStore} 同口径：读 {@code sys_session} 判过期，
 * 会员令牌 username 为 {@code m:{memberId}}，后台令牌为 sys_user.username。
 * 网关只做只读校验（不滑动续期，续期仍由服务侧 AuthContextFilter 负责）。
 *
 * <p>本地缓存 60 秒，避免每个请求都打一次库。
 */
@Component
@Slf4j
public class GatewayTokenResolver {

    private static final long CACHE_TTL_MILLIS = 60_000L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${gateway.auth.member-prefix:m:}")
    private String memberPrefix;

    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> adminIdCache = new ConcurrentHashMap<>();

    /**
     * @return 身份；token 无效/过期返回 null。库异常时抛异常，由调用方决定放行策略
     */
    public Identity resolve(String token) {
        Cached cached = cache.get(token);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.cachedAt() < CACHE_TTL_MILLIS) {
            return cached.identity();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT username, expire_time FROM sys_session WHERE token = ? AND deleted = 0", token);
        if (rows.isEmpty()) {
            cache.remove(token);
            return null;
        }
        Object expireRaw = rows.get(0).get("expire_time");
        LocalDateTime expire = expireRaw instanceof LocalDateTime ldt
                ? ldt : ((Timestamp) expireRaw).toLocalDateTime();
        if (LocalDateTime.now().isAfter(expire)) {
            cache.remove(token);
            return null;
        }
        String username = String.valueOf(rows.get(0).get("username"));
        Identity identity;
        if (username.startsWith(memberPrefix)) {
            identity = new Identity(username, username.substring(memberPrefix.length()), "member");
        } else {
            identity = new Identity(username, adminIdOf(username), "admin");
        }
        cache.put(token, new Cached(identity, now));
        return identity;
    }

    private String adminIdOf(String username) {
        Long cachedId = adminIdCache.get(username);
        if (cachedId != null) {
            return String.valueOf(cachedId);
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ? AND deleted = 0 LIMIT 1", Long.class, username);
        if (ids.isEmpty()) {
            return null;
        }
        adminIdCache.put(username, ids.get(0));
        return String.valueOf(ids.get(0));
    }

    /** 鉴权结果：username 原样、userId 为会员 id 或 sys_user.id、userType 为 member/admin */
    public record Identity(String username, String userId, String userType) {
    }

    private record Cached(Identity identity, long cachedAt) {
    }
}
