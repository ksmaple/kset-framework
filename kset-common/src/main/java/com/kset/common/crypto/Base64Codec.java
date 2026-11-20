package com.kset.common.crypto;

import org.apache.commons.codec.binary.Base64;

import java.util.regex.Pattern;

final class Base64Codec {

    private static final Pattern STANDARD_BASE64 = Pattern.compile(
            "(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}(?:==)?|[A-Za-z0-9+/]{3}=?)?");

    private Base64Codec() {
    }

    static String encode(byte[] value) {
        return Base64.encodeBase64String(value);
    }

    static byte[] decode(String value) {
        // Commons Codec's static decoder ignores invalid characters by default.
        if (!STANDARD_BASE64.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Base64 input");
        }
        return Base64.decodeBase64(value);
    }
}
