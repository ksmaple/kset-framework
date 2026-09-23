package com.kset.agent.sandbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 沙箱安全策略，绑定 yml 中 ai.tool.sandbox.* 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.tool.sandbox")
public class SandboxSecurityPolicy {

    /** 禁止导入的模块（Python import/from 检查） */
    private Set<String> forbiddenModules = Set.of("os", "sys", "subprocess", "urllib", "requests", "socket");

    /** 禁止使用的代码模式 */
    private Set<String> forbiddenPatterns = Set.of("__import__", "eval(", "exec(", "compile(", "open(", "file(");
}
