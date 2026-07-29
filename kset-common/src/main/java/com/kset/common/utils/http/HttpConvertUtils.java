package com.kset.common.utils.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.stream.Collectors;

public class HttpConvertUtils {

    public static String convertMapToHttpGetParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.entrySet().stream()
                .map(entry -> {
                    try {
                        return entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        // 在实际应用中可能需要更好的异常处理
                        throw new RuntimeException("编码参数时出错", e);
                    }
                })
                .collect(Collectors.joining("&"));
    }

    public static String convertMapToHttpFormBody(Map<String, String> params) {
        return convertMapToHttpGetParams(params);
    }

}
