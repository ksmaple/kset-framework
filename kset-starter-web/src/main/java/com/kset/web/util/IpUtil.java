package com.kset.web.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP 请求 IP 获取工具
 */
public final class IpUtil {

    private static final String UNKNOWN = "unknown";

    private IpUtil() {
    }

    /**
     * 从请求头链中获取客户端真实 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (!hasIp(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (!hasIp(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (!hasIp(ip)) {
            ip = request.getRemoteAddr();
        }
        return firstIp(ip);
    }

    private static boolean hasIp(String ip) {
        return ip != null && !ip.isBlank() && !UNKNOWN.equalsIgnoreCase(ip.trim());
    }

    private static String firstIp(String ip) {
        if (ip == null) {
            return null;
        }
        int commaIndex = ip.indexOf(',');
        if (commaIndex < 0) {
            return ip.trim();
        }
        return ip.substring(0, commaIndex).trim();
    }
}
