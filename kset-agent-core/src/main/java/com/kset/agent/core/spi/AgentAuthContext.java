package com.kset.agent.core.spi;

/**
 * Agent 执行期安全上下文（线程级）。
 *
 * <p>宿主应用在请求边界通过 {@link #setCurrentUser} / {@link #setRequestAccessScope} 绑定，
 * 请求结束或线程回收前必须调用 {@link #clear()}。编排执行器在异步工具执行线程内会重新绑定
 * 调用方线程的会话快照，因此实现保持纯 ThreadLocal，不依赖任何具体认证框架。
 */
public final class AgentAuthContext {

    private static final ThreadLocal<AgentUserSession> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<AgentAccessScope> REQUEST_ACCESS_SCOPE = new ThreadLocal<>();

    private AgentAuthContext() {
    }

    public static void setCurrentUser(AgentUserSession session) {
        if (session == null) {
            CURRENT_USER.remove();
        } else {
            CURRENT_USER.set(session);
        }
    }

    public static AgentUserSession getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static void setRequestAccessScope(AgentAccessScope scope) {
        if (scope == null) {
            REQUEST_ACCESS_SCOPE.remove();
        } else {
            REQUEST_ACCESS_SCOPE.set(scope);
        }
    }

    public static AgentAccessScope requestAccessScope() {
        return REQUEST_ACCESS_SCOPE.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
        REQUEST_ACCESS_SCOPE.remove();
    }

    public static Long getUserId() {
        AgentUserSession user = CURRENT_USER.get();
        return user == null ? null : user.getUserId();
    }

    public static boolean isSuperAdmin() {
        AgentUserSession user = CURRENT_USER.get();
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        if (user.getRoles() == null) {
            return false;
        }
        for (String role : user.getRoles()) {
            if ("admin".equalsIgnoreCase(role) || "super_admin".equalsIgnoreCase(role)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasPermission(String permCode) {
        if (permCode == null || permCode.isBlank()) {
            return false;
        }
        AgentUserSession user = CURRENT_USER.get();
        return user != null && (user.hasPermission(permCode) || isSuperAdmin());
    }
}
