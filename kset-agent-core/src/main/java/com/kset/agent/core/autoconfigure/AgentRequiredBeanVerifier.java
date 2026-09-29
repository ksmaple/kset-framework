package com.kset.agent.core.autoconfigure;

import com.kset.agent.core.spi.AgentModelPort;
import com.kset.agent.core.workflow.WorkflowRuntimeStateStore;
import com.kset.agent.core.workflow.WorkflowTaskRepository;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 宿主必配 Bean 的 fail-fast 校验。
 *
 * <p>在 Bean 实例化前校验以下接入点是否齐备，缺失时抛出携带接入指引的
 * {@link IllegalStateException}，避免宿主只能看到 Spring 注入失败的晦涩报错：
 * <ul>
 *   <li>{@link AgentModelPort} —— 模型接入（调用 + 能力/预算描述）</li>
 *   <li>{@link WorkflowTaskRepository} —— 工作流任务持久化</li>
 *   <li>{@link WorkflowRuntimeStateStore} —— 工作流运行时状态存储（确认态/取消标记，宿主自行选择 JDBC/Redis 等实现）</li>
 *   <li>名为 {@code agentToolTaskExecutor} 的 {@link Executor} —— 工具并行执行线程池</li>
 * </ul>
 *
 * <p>可通过 {@code ai.agent.required-bean-check=false} 关闭。
 */
public class AgentRequiredBeanVerifier implements BeanFactoryPostProcessor, EnvironmentAware {

    public static final String CHECK_ENABLED_PROPERTY = "ai.agent.required-bean-check";
    public static final String TOOL_EXECUTOR_BEAN_NAME = "agentToolTaskExecutor";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (environment != null
                && !environment.getProperty(CHECK_ENABLED_PROPERTY, Boolean.class, Boolean.TRUE)) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (beanFactory.getBeanNamesForType(AgentModelPort.class, false, false).length == 0) {
            missing.add(AgentModelPort.class.getSimpleName() + "（模型接入：调用 + 能力/预算描述，宿主必须实现）");
        }
        if (beanFactory.getBeanNamesForType(WorkflowTaskRepository.class, false, false).length == 0) {
            missing.add(WorkflowTaskRepository.class.getSimpleName() + "（工作流任务持久化，宿主必须实现）");
        }
        if (beanFactory.getBeanNamesForType(WorkflowRuntimeStateStore.class, false, false).length == 0) {
            missing.add(WorkflowRuntimeStateStore.class.getSimpleName() + "（工作流运行时状态存储，宿主必须实现）");
        }
        if (!beanFactory.containsBean(TOOL_EXECUTOR_BEAN_NAME)) {
            missing.add("名为 " + TOOL_EXECUTOR_BEAN_NAME + " 的 Executor（工具并行执行线程池，宿主必须提供）");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "kset-agent-core 接入校验失败，缺少宿主必配 Bean：\n - " + String.join("\n - ", missing)
                            + "\n请参考 kset-agent-core README 的接入指引；"
                            + "如需关闭本校验可配置 " + CHECK_ENABLED_PROPERTY + "=false。");
        }
    }
}
