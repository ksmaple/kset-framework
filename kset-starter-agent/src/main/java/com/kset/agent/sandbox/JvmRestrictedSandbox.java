package com.kset.agent.sandbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * JVM 受限沙箱：无需 Docker，支持 JavaScript 和 Python（通过子进程）。
 *
 * <p>安全策略：禁止网络、禁止文件 IO、限制执行时长、禁用危险模块与模式。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.tool.sandbox.enabled", havingValue = "true", matchIfMissing = true)
public class JvmRestrictedSandbox implements CodeSandbox {

    private final SandboxSecurityPolicy policy;

    public JvmRestrictedSandbox(SandboxSecurityPolicy policy) {
        this.policy = policy;
    }

    @Override
    public SandboxResult execute(String language, String code, int timeoutSeconds) {
        if (code == null || code.isBlank()) {
            return new SandboxResult(false, "", "代码为空", 0);
        }
        String normalized = language != null ? language.toLowerCase() : "";
        String securityCheck = checkSecurity(code);
        if (securityCheck != null) {
            return new SandboxResult(false, "", "安全策略拦截: " + securityCheck, 0);
        }

        return switch (normalized) {
            case "js", "javascript" -> executeJs(code, timeoutSeconds);
            case "python", "py" -> executePython(code, timeoutSeconds);
            default -> new SandboxResult(false, "", "不支持的代码语言: " + language, 0);
        };
    }

    private SandboxResult executeJs(String code, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        if (engine == null) {
            return new SandboxResult(false, "", "当前 JVM 未配置 JavaScript 引擎", System.currentTimeMillis() - start);
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "sandbox-js");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<Object> future = executor.submit(() -> engine.eval(wrapJsCode(code)));
            Object result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            String output = result != null ? String.valueOf(result) : "";
            return new SandboxResult(true, output, "", System.currentTimeMillis() - start);
        } catch (TimeoutException e) {
            return new SandboxResult(false, "", "执行超时（" + timeoutSeconds + " 秒）", System.currentTimeMillis() - start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SandboxResult(false, "", e.getMessage(), System.currentTimeMillis() - start);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return new SandboxResult(false, "", cause.getMessage(), System.currentTimeMillis() - start);
        } finally {
            executor.shutdownNow();
        }
    }

    private SandboxResult executePython(String code, int timeoutSeconds) {
        long start = System.currentTimeMillis();
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("sandbox-", ".py");
            Files.writeString(tempFile, wrapPythonCode(code), StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder("python", tempFile.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new SandboxResult(false, "", "执行超时（" + timeoutSeconds + " 秒）", System.currentTimeMillis() - start);
            }

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            int exitCode = process.exitValue();
            return new SandboxResult(exitCode == 0, output, exitCode == 0 ? "" : output,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new SandboxResult(false, "", e.getMessage(), System.currentTimeMillis() - start);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String checkSecurity(String code) {
        String lower = code.toLowerCase();
        for (String module : policy.getForbiddenModules()) {
            if (lower.matches(".*\\b(import\\s+" + module + "|from\\s+" + module + ").*")) {
                return "禁止导入模块: " + module;
            }
        }
        for (String pattern : policy.getForbiddenPatterns()) {
            if (lower.contains(pattern.toLowerCase())) {
                return "禁止使用的模式: " + pattern;
            }
        }
        return null;
    }

    private String wrapJsCode(String code) {
        return """
                (function() {
                    var console = { log: function() { return Array.prototype.slice.call(arguments).join(' '); } };
                    %s
                })()
                """.formatted(code);
    }

    private String wrapPythonCode(String code) {
        return """
                import sys
                sys.stdout = open(sys.stdout.fileno(), mode='w', encoding='utf-8', buffering=1)
                %s
                """.formatted(code);
    }
}
