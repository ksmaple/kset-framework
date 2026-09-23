package com.kset.agent.extension.guardrail;

import com.kset.agent.spi.ContentSecurityPort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ContentSecurityGuardrailRule implements AiGuardrailRule {
    private final ContentSecurityPort contentSecurityService;

    public ContentSecurityGuardrailRule(ContentSecurityPort contentSecurityService) {
        this.contentSecurityService = contentSecurityService;
    }

    @Override
    public String code() {
        return "content-security";
    }

    @Override
    public String sanitizeInput(String input) {
        return contentSecurityService.sanitizeInput(input);
    }

    @Override
    public String sanitizeOutput(String output) {
        return contentSecurityService.sanitizeOutput(output);
    }

    @Override
    public Map<String, Object> inspect(String text, boolean input) {
        String sanitized = input ? sanitizeInput(text) : sanitizeOutput(text);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("checked", text != null && !text.isBlank());
        item.put("changed", text != null && sanitized != null && !text.equals(sanitized));
        item.put("containsSensitiveContent", contentSecurityService.containsSensitiveContent(text));
        item.put("length", text != null ? text.length() : 0);
        item.put("sanitizedLength", sanitized != null ? sanitized.length() : 0);
        return item;
    }
}
