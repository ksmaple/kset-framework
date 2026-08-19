package com.kset.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.kset.common.utils.JsonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 日志敏感数据脱敏工具。
 *
 * <p>支持 JSON 结构化脱敏与纯文本回退脱敏。对匹配敏感字段名的值按规则处理：
 * <ul>
 *   <li>密码/密钥/Token → {@code [REDACTED]}</li>
 *   <li>手机号 → 保留前3后4，中间 ****</li>
 *   <li>邮箱 → 首字符 + *** + @domain</li>
 *   <li>身份证号 → 保留前6后4，中间 ********</li>
 *   <li>银行卡 → 保留前4后4，中间 ****</li>
 *   <li>地址 → 保留前12字符 + ...</li>
 * </ul>
 */
public class LogMaskingUtil {

    private static final Set<String> REDACTED_KEYS = Set.of(
            "password", "pwd", "passwd", "secret", "token",
            "apikey", "api_key", "auth", "authorization", "credential",
            "key", "privatekey", "private_key", "accesskey", "access_key"
    );

    private static final Set<String> PHONE_KEYS = Set.of("phone", "phones", "mobile", "tel", "telephone");
    private static final Set<String> EMAIL_KEYS = Set.of("email", "emails", "mail");
    private static final Set<String> IDCARD_KEYS = Set.of("idcard", "id_card", "identity", "idnumber", "id_number");
    private static final Set<String> BANK_KEYS = Set.of("bankcard", "bank_card", "bankno", "bank_no");
    private static final Set<String> ADDRESS_KEYS = Set.of("address", "addr");

    private LogMaskingUtil() {
    }

    /**
     * 对 JSON 字符串进行敏感字段脱敏。非合法 JSON 时回退到文本正则脱敏。
     */
    public static String maskJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode node = JsonUtil.mapper().readTree(json);
            maskNode(node);
            return JsonUtil.mapper().writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return maskText(json);
        }
    }

    private static void maskNode(JsonNode node) {
        maskNodeByField(node, null);
    }

    /**
     * 保留原因：字段名用 contains 子串匹配，且只处理文本节点，数值与数组标量会漏脱敏。
     */
    @SuppressWarnings("unused")
    private static void maskNodeForRollback(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.fields().forEachRemaining(entry -> {
                String key = entry.getKey().toLowerCase();
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    obj.set(entry.getKey(), TextNode.valueOf(maskValue(key, value.asText())));
                } else if (value.isObject() || value.isArray()) {
                    maskNodeForRollback(value);
                }
            });
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                JsonNode elem = arr.get(i);
                if (elem.isObject() || elem.isArray()) {
                    maskNodeForRollback(elem);
                }
            }
        }
    }

    private static void maskNodeByField(JsonNode node, String parentField) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> fields = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fields::add);
            for (String fieldName : fields) {
                JsonNode value = obj.get(fieldName);
                if (isScalar(value)) {
                    obj.set(fieldName, TextNode.valueOf(maskValue(fieldName, value.asText())));
                } else if (value != null && (value.isObject() || value.isArray())) {
                    maskNodeByField(value, fieldName);
                }
            }
            return;
        }
        if (!node.isArray()) {
            return;
        }
        ArrayNode arr = (ArrayNode) node;
        for (int i = 0; i < arr.size(); i++) {
            JsonNode elem = arr.get(i);
            if (isScalar(elem) && parentField != null) {
                arr.set(i, TextNode.valueOf(maskValue(parentField, elem.asText())));
            } else if (elem.isObject() || elem.isArray()) {
                maskNodeByField(elem, parentField);
            }
        }
    }

    private static boolean isScalar(JsonNode value) {
        return value != null && (value.isTextual() || value.isNumber());
    }

    private static String maskValue(String key, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (keyMatches(key, REDACTED_KEYS)) {
            return "[REDACTED]";
        }
        if (keyMatches(key, PHONE_KEYS)) {
            return maskPhone(value);
        }
        if (keyMatches(key, EMAIL_KEYS)) {
            return maskEmail(value);
        }
        if (keyMatches(key, IDCARD_KEYS)) {
            return maskIdCard(value);
        }
        if (keyMatches(key, BANK_KEYS)) {
            return maskBankCard(value);
        }
        if (keyMatches(key, ADDRESS_KEYS)) {
            return maskAddress(value);
        }
        return value;
    }

    private static boolean keyMatches(String key, Set<String> patterns) {
        return keyMatchesToken(key, patterns);
    }

    /**
     * 保留原因：key.contains 会把 monkey、author 等非子段名误判为敏感字段。
     */
    @SuppressWarnings("unused")
    private static boolean keyMatchesForRollback(String key, Set<String> patterns) {
        for (String p : patterns) {
            if (key.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean keyMatchesToken(String fieldName, Set<String> patterns) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        if (patterns.contains(lower)) {
            return true;
        }
        for (String token : fieldTokens(fieldName)) {
            if (patterns.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> fieldTokens(String fieldName) {
        List<String> tokens = new ArrayList<>();
        for (String part : fieldName.split("[_\\-]+")) {
            if (part.isEmpty()) {
                continue;
            }
            int start = 0;
            for (int i = 1; i < part.length(); i++) {
                char prev = part.charAt(i - 1);
                char cur = part.charAt(i);
                boolean camel = Character.isLowerCase(prev) && Character.isUpperCase(cur);
                boolean upperRun = Character.isUpperCase(prev)
                        && Character.isUpperCase(cur)
                        && i + 1 < part.length()
                        && Character.isLowerCase(part.charAt(i + 1));
                if (camel || upperRun) {
                    tokens.add(part.substring(start, i).toLowerCase());
                    start = i;
                }
            }
            tokens.add(part.substring(start).toLowerCase());
        }
        return tokens;
    }

    private static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskEmail(String email) {
        return maskEmailOrPlain(email);
    }

    /**
     * 保留原因：无 @ 时 substring(-1) 抛 StringIndexOutOfBoundsException。
     */
    @SuppressWarnings("unused")
    private static String maskEmailForRollback(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "****" + email.substring(at);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskEmailOrPlain(String email) {
        int at = email.indexOf('@');
        if (at < 0) {
            return "****";
        }
        if (at <= 1) {
            return "****" + email.substring(at);
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskIdCard(String id) {
        if (id.length() < 10) {
            return "************";
        }
        return id.substring(0, 6) + "********" + id.substring(id.length() - 4);
    }

    private static String maskBankCard(String card) {
        String digits = card.replaceAll("\\s+", "");
        if (digits.length() < 8) {
            return "****";
        }
        return digits.substring(0, 4) + " **** **** **** " + digits.substring(digits.length() - 4);
    }

    private static String maskAddress(String addr) {
        if (addr.length() <= 12) {
            return addr;
        }
        return addr.substring(0, 12) + "...";
    }

    /**
     * 纯文本回退脱敏：对常见 JSON 键值对做简单正则替换。
     */
    public static String maskText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String result = text;
        for (String key : REDACTED_KEYS) {
            result = result.replaceAll(
                    "(?i)(\"" + key + "\":\\s*\")[^\"]*\"",
                    "$1[REDACTED]\""
            );
        }
        return result;
    }
}
