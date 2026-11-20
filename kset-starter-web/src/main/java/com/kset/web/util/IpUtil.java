package com.kset.web.util;

import com.kset.common.net.ClientIp;
import jakarta.servlet.http.HttpServletRequest;

/**
 * HTTP 请求 IP 获取工具。解析顺序见 {@link ClientIp}。
 */
public final class IpUtil {

    private IpUtil() {
    }

    /**
     * 从请求头链中获取客户端真实 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        return ClientIp.resolve(request::getHeader, request.getRemoteAddr());
    }
}
