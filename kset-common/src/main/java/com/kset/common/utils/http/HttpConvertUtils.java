package com.kset.common.utils.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpConvertUtils {

    public static String convertMapToHttpGetParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return encodeParamsSkippingNull(params);
    }

    /**
     * 保留原因：value 为 null 时 URLEncoder.encode 抛 NPE。
     */
    @SuppressWarnings("unused")
    private static String convertMapToHttpGetParamsForRollback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.entrySet().stream()
                .map(entry -> {
                    try {
                        return entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("编码参数时出错", e);
                    }
                })
                .collect(Collectors.joining("&"));
    }

    private static String encodeParamsSkippingNull(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static String convertMapToHttpFormBody(Map<String, String> params) {
        return convertMapToHttpGetParams(params);
    }

}
