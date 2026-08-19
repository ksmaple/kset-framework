package com.kset.common.utils.http;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HttpConvertUtilsTest {

    @Test
    void skipsNullValuesAndKeys() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "1");
        params.put("b", null);
        params.put(null, "x");
        params.put("c", "2");

        assertThatCode(() -> HttpConvertUtils.convertMapToHttpGetParams(params))
                .doesNotThrowAnyException();
        assertThat(HttpConvertUtils.convertMapToHttpGetParams(params)).isEqualTo("a=1&c=2");
        assertThat(HttpConvertUtils.convertMapToHttpFormBody(params)).isEqualTo("a=1&c=2");
    }

    @Test
    void encodesChineseAndEmptyMap() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("name", "中文");

        assertThat(HttpConvertUtils.convertMapToHttpGetParams(params)).isEqualTo("name=%E4%B8%AD%E6%96%87");
        assertThat(HttpConvertUtils.convertMapToHttpGetParams(null)).isEmpty();
        assertThat(HttpConvertUtils.convertMapToHttpGetParams(Map.of())).isEmpty();
    }
}
