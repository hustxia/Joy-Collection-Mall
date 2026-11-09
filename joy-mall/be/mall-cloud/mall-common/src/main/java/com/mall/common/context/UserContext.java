package com.mall.common.context;

/**
 * 当前登录用户上下文（ThreadLocal）
 * 由鉴权过滤器写入，业务代码只读。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<String> DATA_SCOPE_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<Long> DEPT_ID_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void setUsername(String username) {
        USERNAME_HOLDER.set(username);
    }

    public static String getUsername() {
        return USERNAME_HOLDER.get();
    }

    /**
     * 数据权限范围：all / dept / self。
     * 未显式设置时按 self 兜底——漏配宁严不松，不能因为没设置就看到全量。
     */
    public static String getDataScope() {
        String scope = DATA_SCOPE_HOLDER.get();
        return scope == null || scope.isBlank() ? "self" : scope;
    }

    public static void setDataScope(String dataScope) {
        DATA_SCOPE_HOLDER.set(dataScope);
    }

    public static void setDeptId(Long deptId) {
        DEPT_ID_HOLDER.set(deptId);
    }

    public static Long getDeptId() {
        return DEPT_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
        USERNAME_HOLDER.remove();
        DATA_SCOPE_HOLDER.remove();
        DEPT_ID_HOLDER.remove();
    }
}
