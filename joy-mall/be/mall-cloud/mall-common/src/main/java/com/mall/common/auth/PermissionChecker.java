package com.mall.common.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接口权限点集中校验（V6.4 P0-3）。
 *
 * <p>映射来源：所有 {@link ApiPermContributor} bean（业务表生成 {@code XxxApiPerms}，
 * 基座自带 Flow/Pay 豁免清单）。用户 perms 从 sys_user_role→sys_role_menu.perms 加载，60s 缓存。
 * admin 角色（role_key=admin 或 id=1）直通。
 * <p>安全缺省 fail-closed：映射缺失时 GET 放行、写方法拒绝——存量系统升级后
 * 宁可补一次授权配置，也不能让写接口继续裸奔。
 */
@Component
public class PermissionChecker {

    private static final long CACHE_TTL_MS = 60_000L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, String> endpointPerms = new ConcurrentHashMap<>();
    private final Map<String, PermsCacheEntry> permsCache = new ConcurrentHashMap<>();
    private final Map<String, UserIdCacheEntry> userIdCache = new ConcurrentHashMap<>();

    public PermissionChecker(List<ApiPermContributor> contributors) {
        for (ApiPermContributor contributor : contributors) {
            endpointPerms.putAll(contributor.endpointPerms());
        }
    }

    public boolean check(HttpServletRequest request, String username) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String method = request.getMethod().toUpperCase();
        String required = resolveRequired(method, path);
        if (required == null) {
            // 未注册映射：管理员直通（基座 sys 端点未注册权限点，超管必须可用）；
            // 普通用户读放行、写拒绝（fail-closed）
            if (hasPerm(permsOf(username), "*:*:*")) {
                return true;
            }
            return "GET".equals(method);
        }
        if (required.isEmpty()) {
            // 仅登录即可（基座能力豁免）
            return true;
        }
        return hasPerm(permsOf(username), required);
    }

    /** 先精确匹配，再按段通配（/{id} 段匹配任意一段） */
    private String resolveRequired(String method, String path) {
        String exact = endpointPerms.get(method + " " + path);
        if (exact != null) {
            return exact;
        }
        String[] actualSegs = path.split("/");
        for (Map.Entry<String, String> entry : endpointPerms.entrySet()) {
            String[] keySegs = entry.getKey().split(" ");
            if (keySegs.length != 2 || !keySegs[0].equals(method)) {
                continue;
            }
            if (segmentMatch(keySegs[1].split("/"), actualSegs)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean segmentMatch(String[] pattern, String[] actual) {
        if (pattern.length != actual.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            String p = pattern[i];
            if (p.startsWith("{") && p.endsWith("}")) {
                if (actual[i].isEmpty()) {
                    return false;
                }
            } else if (!p.equals(actual[i])) {
                return false;
            }
        }
        return true;
    }

    /** perms 匹配：精确值、前缀通配（demo_fee:*）、超级通配（*:*:*） */
    private boolean hasPerm(Set<String> perms, String required) {
        if (perms.contains("*:*:*") || perms.contains(required)) {
            return true;
        }
        int i = required.lastIndexOf(':');
        return i > 0 && perms.contains(required.substring(0, i) + ":*");
    }

    private Set<String> permsOf(String username) {
        PermsCacheEntry cached = permsCache.get(username);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiredAt() > now) {
            return cached.perms();
        }
        Set<String> perms = loadPerms(username);
        permsCache.put(username, new PermsCacheEntry(perms, now + CACHE_TTL_MS));
        return perms;
    }

    private Set<String> loadPerms(String username) {
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ? AND deleted = 0", Long.class, username);
        if (userIds.isEmpty()) {
            return Set.of();
        }
        Integer admin = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE ur.user_id = ? AND r.deleted = 0 AND (r.role_key = 'admin' OR r.id = 1)",
                Integer.class, userIds.get(0));
        if (admin != null && admin > 0) {
            return Set.of("*:*:*");
        }
        List<String> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT m.perms FROM sys_user_role ur "
                        + "JOIN sys_role_menu rm ON rm.role_id = ur.role_id "
                        + "JOIN sys_menu m ON m.id = rm.menu_id "
                        + "WHERE ur.user_id = ? AND m.deleted = 0 AND m.perms IS NOT NULL AND m.perms <> ''",
                String.class, userIds.get(0));
        return new HashSet<>(rows);
    }

    /**
     * 用户名 → 用户ID（60s 缓存）。鉴权过滤器填充 {@code UserContext} 用：
     * 修 A-01——setUserId 从未被调用导致审计字段/操作日志拿不到 userId。
     */
    public Long userIdOf(String username) {
        long now = System.currentTimeMillis();
        UserIdCacheEntry cached = userIdCache.get(username);
        if (cached != null && cached.expiredAt() > now) {
            return cached.userId();
        }
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM sys_user WHERE username = ? AND deleted = 0", Long.class, username);
        Long id = ids.isEmpty() ? null : ids.get(0);
        userIdCache.put(username, new UserIdCacheEntry(id, now + CACHE_TTL_MS));
        return id;
    }

    private record PermsCacheEntry(Set<String> perms, long expiredAt) {
    }

    private record UserIdCacheEntry(Long userId, long expiredAt) {
    }
}
