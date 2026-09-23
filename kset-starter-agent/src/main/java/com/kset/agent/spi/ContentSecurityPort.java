package com.kset.agent.spi;

/**
 * 内容安全端口：输入/输出清洗与敏感内容识别。
 */
public interface ContentSecurityPort {
    String sanitizeInput(String input);
    String sanitizeOutput(String output);
    boolean containsSensitiveContent(String text);
}
