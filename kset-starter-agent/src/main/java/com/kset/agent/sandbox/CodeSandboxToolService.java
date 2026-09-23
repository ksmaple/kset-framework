package com.kset.agent.sandbox;

import com.kset.agent.tool.ToolExecutionException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 沙箱代码执行工具：供 Agent 调用
 */
@Component
public class CodeSandboxToolService {

    private final CodeSandbox codeSandbox;

    public CodeSandboxToolService(CodeSandbox codeSandbox) {
        this.codeSandbox = codeSandbox;
    }

    @Tool(description = "在受限环境中执行 Python 或 JavaScript 代码，返回执行结果。支持数据计算、文本处理、简单脚本。")
    public String executeCode(
            @ToolParam(description = "代码语言: python / javascript") String language,
            @ToolParam(description = "要执行的代码") String code,
            @ToolParam(description = "超时时间(秒), 默认 30") int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 30;
        }
        SandboxResult result = codeSandbox.execute(language, code, timeoutSeconds);
        if (result.success()) {
            return result.output();
        }
        throw new ToolExecutionException("SANDBOX_EXECUTION_FAILED", "执行失败: " + result.error());
    }
}
