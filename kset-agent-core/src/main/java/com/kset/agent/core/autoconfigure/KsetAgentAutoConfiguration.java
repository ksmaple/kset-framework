package com.kset.agent.core.autoconfigure;

import com.kset.agent.core.dto.AiSessionQualityDTO;
import com.kset.agent.core.dto.AiSessionQualityStatsDTO;
import com.kset.agent.core.dto.LlmHealthSnapshotDTO;
import com.kset.agent.core.port.AgentWorkflowMetricsPort;
import com.kset.agent.core.port.AiFlowMetricsPort;
import com.kset.agent.core.port.AiSessionQualityQueryPort;
import com.kset.agent.core.port.LlmHealthPort;
import com.kset.agent.core.port.ToolCallMetricsPort;
import com.kset.agent.core.spi.AgentMessagePort;
import com.kset.agent.core.spi.AgentRuntimeConfigPort;
import com.kset.agent.core.spi.CodeRepositoryAccessPort;
import com.kset.agent.core.spi.ContentSecurityPort;
import com.kset.agent.core.spi.DocumentAccessPort;
import com.kset.agent.core.spi.IndexedProjectScopePort;
import com.kset.agent.core.spi.ModelPricingPort;
import com.kset.agent.core.spi.ProjectAccessPort;
import com.kset.agent.core.tool.InMemoryToolRegistry;
import com.kset.agent.core.tool.ToolRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;

import java.time.LocalDate;
import java.util.List;

/**
 * KSet Agent 编排能力自动装配。
 *
 * <p>扫描 {@code com.kset.agent.core} 下的编排组件（ReAct 执行器、工具调度、工作流引擎、
 * 沙箱与扩展点），并为各 SPI 端口提供可被覆盖的默认实现。
 *
 * <p>宿主应用必须提供的 Bean（由 {@link AgentRequiredBeanVerifier} 在启动时 fail-fast 校验，
 * 可通过 {@code ai.agent.required-bean-check=false} 关闭）：
 * <ul>
 *   <li>{@link com.kset.agent.core.spi.AgentModelPort} —— 模型接入（调用 + 能力/预算描述）</li>
 *   <li>{@link com.kset.agent.core.workflow.WorkflowTaskRepository} —— 工作流任务持久化</li>
 *   <li>{@link com.kset.agent.core.workflow.WorkflowRuntimeStateStore} —— 工作流运行时状态存储</li>
 *   <li>名为 {@code agentToolTaskExecutor} 的 {@link java.util.concurrent.Executor} —— 工具并行执行线程池</li>
 * </ul>
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.kset.agent.core")
public class KsetAgentAutoConfiguration {

    @Bean
    public static AgentRequiredBeanVerifier agentRequiredBeanVerifier() {
        return new AgentRequiredBeanVerifier();
    }

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

    /** 默认不降级；具备 LLM 健康监控的宿主应用应覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public LlmHealthPort llmHealthPort() {
        return new LlmHealthPort() {
            @Override
            public boolean isDegraded() {
                return false;
            }

            @Override
            public LlmHealthSnapshotDTO snapshot() {
                return new LlmHealthSnapshotDTO(false, 0, 0, 0, null);
            }
        };
    }

    /** 默认无会话质量数据；具备质量统计能力的宿主应用应覆盖。 */
    @Bean
    @ConditionalOnMissingBean
    public AiSessionQualityQueryPort aiSessionQualityQueryPort() {
        return new AiSessionQualityQueryPort() {
            @Override
            public List<AiSessionQualityDTO> list(com.kset.agent.core.dto.ListAiSessionQualityCommand command) {
                return List.of();
            }

            @Override
            public List<AiSessionQualityStatsDTO> dimensionStats(LocalDate startDate, LocalDate endDate) {
                return List.of();
            }

            @Override
            public List<AiSessionQualityStatsDTO> dailyStats(LocalDate startDate, LocalDate endDate, String dimension) {
                return List.of();
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
