package com.kset.agent.autoconfigure;

import com.kset.agent.port.AgentWorkflowMetricsPort;
import com.kset.agent.port.AiFlowMetricsPort;
import com.kset.agent.port.ToolCallMetricsPort;
import com.kset.agent.spi.AgentMessagePort;
import com.kset.agent.spi.AgentRuntimeConfigPort;
import com.kset.agent.spi.CodeRepositoryAccessPort;
import com.kset.agent.spi.ContentSecurityPort;
import com.kset.agent.spi.DocumentAccessPort;
import com.kset.agent.spi.IndexedProjectScopePort;
import com.kset.agent.spi.ModelPricingPort;
import com.kset.agent.spi.ProjectAccessPort;
import com.kset.agent.tool.InMemoryToolRegistry;
import com.kset.agent.tool.ToolRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * KSet Agent 编排能力自动装配。
 *
 * <p>扫描 {@code com.kset.agent} 下的编排组件（ReAct 执行器、工具调度、工作流引擎、
 * 沙箱与扩展点），并为各 SPI 端口提供可被覆盖的默认实现。
 *
 * <p>宿主应用必须提供的 Bean：
 * <ul>
 *   <li>{@link com.kset.agent.spi.AgentChatModelPort} —— 模型调用</li>
 *   <li>{@link com.kset.agent.model.AiModelProvider} —— 模型能力描述</li>
 *   <li>{@link com.kset.agent.workflow.WorkflowTaskRepository} —— 工作流任务持久化</li>
 *   <li>名为 {@code agentToolTaskExecutor} 的 {@link java.util.concurrent.Executor} —— 工具并行执行线程池</li>
 * </ul>
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.kset.agent")
public class KsetAgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new InMemoryToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentRuntimeConfigPort agentRuntimeConfigPort(Environment environment) {
        return new AgentRuntimeConfigPort() {
            @Override
            public int intValue(String key, int defaultValue) {
                return environment.getProperty(key, Integer.class, defaultValue);
            }

            @Override
            public boolean boolValue(String key, boolean defaultValue) {
                return environment.getProperty(key, Boolean.class, defaultValue);
            }

            @Override
            public String stringValue(String key, String defaultValue) {
                return environment.getProperty(key, defaultValue);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentMessagePort agentMessagePort() {
        return new AgentMessagePort() {
            @Override
            public String get(String code) {
                return code;
            }

            @Override
            public String finalAnswerTitle() {
                return "最终回答";
            }

            @Override
            public String languageForModel() {
                return "auto";
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ContentSecurityPort contentSecurityPort() {
        return new ContentSecurityPort() {
            @Override
            public String sanitizeInput(String input) {
                return input;
            }

            @Override
            public String sanitizeOutput(String output) {
                return output;
            }

            @Override
            public boolean containsSensitiveContent(String text) {
                return false;
            }
        };
    }

    /** 默认不启用文档级过滤（返回 null）；接入文档体系的宿主应用应覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public DocumentAccessPort documentAccessPort() {
        return () -> null;
    }

    @Bean
    @ConditionalOnMissingBean
    public ModelPricingPort modelPricingPort() {
        return List::of;
    }

    @Bean
    @ConditionalOnMissingBean
    public IndexedProjectScopePort indexedProjectScopePort() {
        return List::of;
    }

    @Bean
    @ConditionalOnMissingBean
    public CodeRepositoryAccessPort codeRepositoryAccessPort() {
        return List::of;
    }

    /** 默认宽松放行（无项目体系）；多项目宿主应用必须覆盖以做真实成员校验。 */
    @Bean
    @ConditionalOnMissingBean
    public ProjectAccessPort projectAccessPort() {
        return new ProjectAccessPort() {
            @Override
            public boolean projectExists(Long projectId) {
                return true;
            }

            @Override
            public boolean isActiveMember(Long projectId, Long userId) {
                return true;
            }

            @Override
            public boolean canAccessProject(Long projectId) {
                return true;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolCallMetricsPort toolCallMetricsPort() {
        return (toolName, taskId, durationMs, success, errorMessage, userId) -> {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentWorkflowMetricsPort agentWorkflowMetricsPort() {
        return (taskId, mode, totalSteps, toolCallCount, finished, durationMs, errorMessage, userId) -> {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public AiFlowMetricsPort aiFlowMetricsPort() {
        return (eventType, taskId, resourceType, success, durationMs, cacheHit, detail) -> {
        };
    }
}
