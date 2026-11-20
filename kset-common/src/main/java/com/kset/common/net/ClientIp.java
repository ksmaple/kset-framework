package com.kset.common.net;

import java.util.function.Function;

/**
 * 从代理头和远端地址解析客户端 IP。Servlet 与响应式网关共用同一套顺序。
 */
public final class ClientIp {

    public static final String UNKNOWN = "unknown";

    private static final String LOCALHOST_IPV4 = "127.0.0.1";

    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    private static final String[] HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private ClientIp() {
    }

    /**
     * @param header        按头名称取值，没有该头时返回 null
     * @param remoteAddress 连接对端地址，可以是 null
     */
    public static String resolve(Function<String, String> header, String remoteAddress) {
        if (header != null) {
            for (String name : HEADERS) {
                String ip = firstHop(header.apply(name));
                if (isValid(ip)) {
                    return normalize(ip);
                }
            }
        }
        String remote = firstHop(remoteAddress);
        if (!isValid(remote)) {
            return UNKNOWN;
        }
        return normalize(remote);
    }

    public static boolean isInternal(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if (LOCALHOST_IPV4.equals(ip) || LOCALHOST_IPV6.equals(ip) || "::1".equals(ip)) {
            return true;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            if (first == 10) {
                return true;
            }
            if (first == 172 && second >= 16 && second <= 31) {
                return true;
            }
            return first == 192 && second == 168;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    private static boolean isValid(String ip) {
        return ip != null && !ip.isEmpty() && !UNKNOWN.equalsIgnoreCase(ip);
    }

    private static String firstHop(String ip) {
        if (ip == null) {
            return null;
        }
        String trimmed = ip.trim();
        int comma = trimmed.indexOf(',');
        if (comma < 0) {
            return trimmed;
        }
        return trimmed.substring(0, comma).trim();
    }

    private static String normalize(String ip) {
        if (LOCALHOST_IPV6.equals(ip) || "::1".equals(ip)) {
            return LOCALHOST_IPV4;
        }
        return ip;
    }
}
