package com.kset.agent.core.spi;

import lombok.Data;

import java.util.Date;
import java.util.Set;

/**
 * Agent 用户会话数据对象（框架级安全上下文载体）。
 *
 * <p>由宿主应用在请求边界绑定，结构与常见登录会话字段保持一致。
 */
@Data
public class AgentUserSession {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private boolean superAdmin;
    private Set<String> permissions;
    private Set<String> roles;
    private Date loginAt;
    private Date expireAt;

    public boolean isExpired() {
        return expireAt != null && new Date().after(expireAt);
    }

    public boolean isUsable() {
        return userId != null && !isExpired();
    }

    public boolean hasPermission(String permCode) {
        return superAdmin || (permissions != null && permissions.contains(permCode));
    }
}
