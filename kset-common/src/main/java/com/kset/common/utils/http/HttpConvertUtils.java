package com.kset.common.utils.http;

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

    private static String encodeParamsSkippingNull(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static String convertMapToHttpFormBody(Map<String, String> params) {
        return convertMapToHttpGetParams(params);
    }

}
