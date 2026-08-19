package com.kset.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * 对象深度拷贝：通过 JSON 树转换生成与源对象引用独立的新实例。
 */
public final class CopyUtil {

    private CopyUtil() {
    }

    public static <T> T deepCopy(Object source, Class<T> targetType) {
        return JsonUtil.copy(source, targetType);
    }

    public static <T> T deepCopy(Object source, TypeReference<T> targetType) {
        return JsonUtil.copy(source, targetType);
    }
}
