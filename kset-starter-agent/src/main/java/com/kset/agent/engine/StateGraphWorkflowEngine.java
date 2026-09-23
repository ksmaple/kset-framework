package com.kset.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;

import com.kset.agent.dto.AgentWorkflowRequest;
import com.kset.agent.dto.AgentWorkflowResult;
import com.kset.agent.dto.AgentExecutionSnapshot;
import com.kset.agent.dto.ChatStreamEvent;
import com.kset.agent.core.AgentReActExecutor;
import com.kset.agent.extension.workflow.WorkflowExecutionExtension;
import com.kset.agent.workflow.WorkflowStep;
import com.kset.agent.workflow.WorkflowTask;
import com.kset.agent.workflow.WorkflowTaskRepository;
import com.kset.agent.spi.ProjectAccessPort;
import com.kset.agent.workflow.StepStatus;
import com.kset.agent.workflow.TaskStatus;
import com.kset.agent.workflow.AgentExecutionStage;
import com.kset.agent.workflow.AgentExecutionLifecycle;
import com.kset.agent.workflow.WorkflowEngine;
import com.kset.agent.workflow.WorkflowTaskStatus;
import com.kset.agent.workflow.WorkflowRuntimeStateStore;
import com.kset.agent.workflow.AgentOutputType;
import com.kset.agent.workflow.AgentStepType;
import com.kset.agent.context.AgentExecutionContext;
import com.kset.agent.stream.ChatStreamContext;
import com.kset.agent.spi.AgentAuthContext;
import com.kset.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于状态图思想的工作流引擎实现
 *
 * <p>包装通用 Agent 执行器，提供任务级持久化、异步执行与状态查询能力。
 * 步骤级持久化由执行器回调逐步增强。
 */
@Slf4j
@Component
public class StateGraphWorkflowEngine implements WorkflowEngine {

    private static final long CANCEL_TTL_HOURS = 24;
    private static final String AGENT_MODE = "react";
    private final String ownerId = UUID.randomUUID().toString();

    private final AgentReActExecutor reActExecutor;
    private final WorkflowTaskRepository taskRepository;
    private final ProjectAccessPort projectAccessPort;
    private final WorkflowRuntimeStateStore runtimeStateStore;
    private final ObjectMapper objectMapper;
    private final List<WorkflowExecutionExtension> extensions;
    private final TaskScheduler taskScheduler;
    private final long leaseSeconds;
    private final long timeoutSeconds;

    public StateGraphWorkflowEngine(AgentReActExecutor reActExecutor,
                                    WorkflowTaskRepository taskRepository,
                                    ProjectAccessPort projectAccessPort,
                                    WorkflowRuntimeStateStore runtimeStateStore,
                                    ObjectMapper objectMapper,
                                    List<WorkflowExecutionExtension> extensions,
                                    TaskScheduler taskScheduler,
                                    @Value("${ai.workflow.execution.lease-seconds:60}") long leaseSeconds,
                                    @Value("${ai.workflow.execution.timeout-seconds:300}") long timeoutSeconds) {
        this.reActExecutor = reActExecutor;
        this.taskRepository = taskRepository;
        this.projectAccessPort = projectAccessPort;
        this.runtimeStateStore = runtimeStateStore;
        this.objectMapper = objectMapper;
        this.taskScheduler = taskScheduler;
        this.leaseSeconds = Math.max(15L, leaseSeconds);
        this.timeoutSeconds = Math.max(30L, timeoutSeconds);
        this.extensions = extensions.stream()
                .sorted(Comparator.comparingInt(WorkflowExecutionExtension::order)).toList();
    }

    @Override
    public AgentWorkflowResult execute(AgentWorkflowRequest request) {
        String taskId = generateTaskId();
        WorkflowTask task = createTask(request, taskId);
        taskRepository.save(task);
        long fencingToken = claimExecution(taskId);
        ChatStreamContext.notifyTaskId(taskId);
        log.info("AI 工作流创建: taskId={}, userId={}, maxSteps={}", taskId, task.getUserId(), task.getMaxSteps());
        return doExecute(task, request, fencingToken);
    }

    @Override
    public AgentWorkflowResult resume(String taskId, AgentWorkflowRequest request) {
        WorkflowTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("工作流任务不存在: " + taskId));
        ensureProjectActive(task);
        ensureTaskOwner(task);
        validateRequestProjectScope(request);
        if (task.getStatus() != TaskStatus.WAITING_INPUT) {
            throw new IllegalStateException("工作流任务不处于等待输入状态: " + taskId);
        }
        request.setExecutionSnapshot(readExecutionSnapshot(task));
        long fencingToken = claimExecution(taskId);
        ChatStreamContext.notifyTaskId(taskId);
        log.info("AI 工作流恢复: taskId={}, userId={}", taskId, task.getUserId());
        return doExecute(task, request, fencingToken);
    }

    @Override
    public AgentWorkflowResult resumeInterrupted(String taskId, boolean retryUnknownTool) {
        WorkflowTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException("工作流任务不存在"));
        ensureProjectActive(task);
        if (task.getUserId() == null || !task.getUserId().equals(AgentAuthContext.getUserId())) {
            throw new BusinessException("无权恢复该工作流任务");
        }
        if (task.getStatus() != TaskStatus.PAUSED) {
            throw new BusinessException("工作流任务不处于可恢复状态");
        }
        List<WorkflowStep> checkpoints = taskRepository.findStepsByTaskId(taskId);
        boolean unknownToolResult = checkpoints.stream().anyMatch(step -> step.getStatus() == StepStatus.RUNNING
                && "TOOL".equalsIgnoreCase(step.getExecutionType()));
        if (unknownToolResult) {
            String retryNotice = retryUnknownTool ? "；不允许直接重试" : "";
            throw new BusinessException("工具节点执行结果未知，需要核验权威执行结果" + retryNotice);
        }
        AgentWorkflowRequest request;
        try {
            request = objectMapper.readValue(task.getRequestSnapshot(), AgentWorkflowRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException("读取工作流恢复快照失败", e);
        }
        request.setInitialStepOffset(task.getCurrentStep());
        request.setExecutionSnapshot(readExecutionSnapshot(task));
        request.setInputContext(buildRecoveryContext(request.getInputContext(), checkpoints));
        validateRequestProjectScope(request);
        long fencingToken = claimExecution(taskId);
        ChatStreamContext.notifyTaskId(taskId);
        Optional<WorkflowStep> completedFinalStep = findCompletedFinalStep(checkpoints);
        if (task.getExecutionStage() == AgentExecutionStage.PERSISTING_MEMORY
                || completedFinalStep.isPresent()) {
            return completeRecoveredAnswer(task, request, fencingToken, completedFinalStep.orElse(null));
        }
        return doExecute(task, request, fencingToken);
    }

    private Optional<WorkflowStep> findCompletedFinalStep(List<WorkflowStep> checkpoints) {
        return checkpoints.stream()
                .filter(step -> step.getStatus() == StepStatus.COMPLETED)
                .filter(step -> AgentOutputType.FINAL_ANSWER.matches(step.getNodeType())
                        || "finish".equalsIgnoreCase(step.getAction()))
                .reduce((previous, current) -> current);
    }

    private AgentWorkflowResult completeRecoveredAnswer(WorkflowTask task, AgentWorkflowRequest request,
                                                         long fencingToken, WorkflowStep finalStep) {
        task.setOwnerId(ownerId);
        task.setFencingToken(fencingToken);
        updateTaskStatus(task, TaskStatus.RUNNING, null);
        String answer = firstNonBlank(task.getFinalAnswer(),
                finalStep != null ? finalStep.getActionInput() : null,
                finalStep != null ? finalStep.getOutputText() : null);
        AgentWorkflowResult result = new AgentWorkflowResult();
        result.setTaskId(task.getTaskId());
        result.setMode(task.getMode());
        result.setAnswer(answer);
        result.setFinished(true);
        result.setRequiresConfirmation(false);
        result.setTotalSteps(task.getCurrentStep());
        if (request.getExecutionSnapshot() != null) {
            result.setMemoryPriority(request.getExecutionSnapshot().getMemoryPriority());
            result.setMemorySummary(request.getExecutionSnapshot().getMemorySummary());
        }
        if (task.getExecutionStage() != AgentExecutionStage.PERSISTING_MEMORY) {
            transitionExecutionStage(task, AgentExecutionStage.PERSISTING_MEMORY);
        }
        result.setExecutionStage(AgentExecutionStage.PERSISTING_MEMORY.code());
        task.setFinalAnswer(answer);
        try {
            notifyAfterExecute(task.getTaskId(), task.getUserId(), request, result);
        } catch (CriticalWorkflowExtensionException extensionError) {
            return pauseForCriticalExtensionFailure(task, result, extensionError);
        }
        task.setFinished(true);
        updateTaskStatus(task, TaskStatus.COMPLETED, null);
        result.setTaskStatus(TaskStatus.COMPLETED.name());
        return result;
    }

    private void ensureProjectActive(WorkflowTask task) {
        if (task.getProjectId() != null && !projectAccessPort.projectExists(task.getProjectId())) {
            throw new BusinessException("工作流关联项目已删除，无法继续执行");
        }
    }

    private void ensureTaskOwner(WorkflowTask task) {
        if (task.getUserId() == null || !task.getUserId().equals(AgentAuthContext.getUserId())) {
            throw new BusinessException("无权恢复该工作流任务");
        }
    }

    private void validateRequestProjectScope(AgentWorkflowRequest request) {
        Long userId = AgentAuthContext.getUserId();
        if (userId == null) {
            throw new BusinessException("当前登录态已失效，无法恢复工作流");
        }
        if (request.getProjectId() != null && !projectAccessPort.isActiveMember(
                request.getProjectId(), userId)
                && !AgentAuthContext.isSuperAdmin()) {
            throw new BusinessException("工作流目标项目已无权访问，无法继续执行");
        }
        if (request.getProjectIds() != null) {
            request.setProjectIds(request.getProjectIds().stream()
                    .filter(id -> projectAccessPort.isActiveMember(id, userId)
                            || AgentAuthContext.isSuperAdmin())
                    .distinct().toList());
        }
    }

    private String buildRecoveryContext(String original, List<WorkflowStep> checkpoints) {
        StringBuilder context = new StringBuilder(original == null ? "" : original);
        context.append("\n\n服务重启后从检查点恢复。以下节点已经完成，不得重复执行：\n");
        checkpoints.stream()
                .filter(step -> step.getStatus() == StepStatus.COMPLETED)
                .forEach(step -> context.append("- ").append(step.getExecutionId()).append(": ")
                        .append(firstNonBlank(step.getObservation(), step.getOutputText(), "已完成")).append('\n'));
        return context.toString();
    }

    private AgentWorkflowResult doExecute(WorkflowTask task, AgentWorkflowRequest request, long fencingToken) {
        String taskId = task.getTaskId();
        task.setOwnerId(ownerId);
        task.setFencingToken(fencingToken);
        AtomicBoolean leaseLost = new AtomicBoolean(false);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();
        request.setCheckpointCallback(step -> saveCheckpoint(task, request, step, fencingToken));
        request.setExecutionStageCallback(stage -> transitionExecutionStage(task, stage));
        request.setTaskStatusCallback(status -> {
            updateRuntimeTaskStatus(task, status);
            ChatStreamContext.emit(ChatStreamEvent.status(status.name()));
        });
        request.setStopRequested(() -> {
            if (System.nanoTime() >= deadlineNanos) {
                timedOut.set(true);
                return true;
            }
            return leaseLost.get() || isCancelled(taskId);
        });
        ScheduledFuture<?> leaseRenewal = startLeaseRenewal(taskId, fencingToken, leaseLost);
        AgentWorkflowResult result = null;
        try (AgentExecutionContext.Scope ignored = AgentExecutionContext.withTaskId(taskId)) {
            try {
                notifyBeforeExecute(taskId, request);
                refreshTaskSnapshot(task, request);
                updateTaskStatus(task, TaskStatus.RUNNING, null);
                if (isCancelled(taskId)) {
                    updateTaskStatus(task, TaskStatus.CANCELLED, null);
                    result = buildFallbackResult(taskId, AGENT_MODE, "任务已取消");
                    notifyAfterExecute(taskId, task.getUserId(), request, result);
                    return result;
                }
                result = reActExecutor.execute(request, taskId);
                if (timedOut.get()) {
                    task.setCurrentStep(result.getTotalSteps());
                    task.setFinalAnswer(result.getAnswer());
                    task.setFinished(false);
                    updateTaskStatus(task, TaskStatus.TIMEOUT, null);
                    result.setFinished(false);
                    notifyAfterExecute(taskId, task.getUserId(), request, result);
                    return result;
                }
                if (isCancelled(taskId)) {
                    task.setCurrentStep(result.getTotalSteps());
                    task.setFinalAnswer(result.getAnswer());
                    task.setFinished(false);
                    updateTaskStatus(task, TaskStatus.CANCELLED, null);
                    result.setFinished(false);
                    notifyAfterExecute(taskId, task.getUserId(), request, result);
                    return result;
                }
                task.setCurrentStep(result.getTotalSteps());
                task.setFinalAnswer(result.getAnswer());
                task.setFinished(result.isFinished());
                TaskStatus finalStatus = resolveFinalStatus(result);
                if (finalStatus == TaskStatus.COMPLETED) {
                    transitionExecutionStage(task, AgentExecutionStage.PERSISTING_MEMORY);
                    result.setExecutionStage(task.getExecutionStage().code());
                    try {
                        notifyAfterExecute(taskId, task.getUserId(), request, result);
                        updateTaskStatus(task, finalStatus, null);
                    } catch (CriticalWorkflowExtensionException extensionError) {
                        result = pauseForCriticalExtensionFailure(task, result, extensionError);
                    }
                } else {
                    updateTaskStatus(task, finalStatus, null);
                    notifyAfterExecute(taskId, task.getUserId(), request, result);
                }
            } catch (Exception e) {
                notifyError(taskId, request, e instanceof RuntimeException runtime
                        ? runtime : new IllegalStateException(e));
                log.error("AI 工作流失败: taskId={}", taskId, e);
                try {
                    updateTaskStatus(task, TaskStatus.FAILED, e.getMessage());
                } catch (RuntimeException staleWriter) {
                    log.error("工作流失败状态未写入，执行租约可能已失效: taskId={}", taskId, staleWriter);
                }
                result = buildFallbackResult(taskId, AGENT_MODE, e.getMessage());
            } finally {
                leaseRenewal.cancel(false);
                if (result != null) {
                    result.setTaskStatus(task.getStatus() != null ? task.getStatus().name() : null);
                    result.setExecutionStage(task.getExecutionStage() != null
                            ? task.getExecutionStage().code() : null);
                }
            }
        }

        return result;
    }

    private void notifyBeforeExecute(String taskId, AgentWorkflowRequest request) {
        for (WorkflowExecutionExtension extension : extensions) {
            extension.beforeExecute(taskId, request);
        }
    }

    private void notifyAfterExecute(String taskId, Long userId,
                                    AgentWorkflowRequest request, AgentWorkflowResult result) {
        for (WorkflowExecutionExtension extension : extensions) {
            try {
                extension.afterExecute(taskId, userId, request, result);
            } catch (RuntimeException extensionError) {
                if (extension.critical()) {
                    throw new CriticalWorkflowExtensionException(extension.code(), extensionError);
                }
                log.warn("工作流完成扩展异常: code={}, taskId={}, error={}",
                        extension.code(), taskId, extensionError.getMessage());
            }
        }
    }

    private void notifyError(String taskId, AgentWorkflowRequest request, RuntimeException error) {
        for (WorkflowExecutionExtension extension : extensions) {
            try {
                extension.onError(taskId, request, error);
            } catch (RuntimeException extensionError) {
                log.warn("工作流扩展异常: code={}, taskId={}, error={}",
                        extension.code(), taskId, extensionError.getMessage());
            }
        }
    }

    @Override
    public WorkflowTaskStatus queryStatus(String taskId) {
        Optional<WorkflowTask> optional = taskRepository.findByTaskId(taskId);
        if (optional.isEmpty()) {
            return null;
        }
        WorkflowTask task = optional.get();
        WorkflowTaskStatus status = new WorkflowTaskStatus();
        status.setTaskId(task.getTaskId());
        status.setStatus(task.getStatus());
        status.setExecutionStage(task.getExecutionStage() != null ? task.getExecutionStage().code() : null);
        status.setMode(task.getMode());
        status.setCurrentStep(task.getCurrentStep());
        status.setTotalSteps(task.getMaxSteps());
        status.setFinalAnswer(task.getFinalAnswer());
        status.setFinished(task.isFinished());
        List<WorkflowTaskStatus.Step> steps = toStatusSteps(taskRepository.findStepsByTaskId(taskId));
        status.setSteps(steps);
        if (!steps.isEmpty()) {
            status.setTotalSteps(steps.size());
        }
        status.setCreatedAt(task.getCreatedAt());
        status.setUpdatedAt(task.getUpdatedAt());
        return status;
    }

    @Override
    public boolean cancel(String taskId) {
        runtimeStateStore.requestCancellation(taskId, Duration.ofHours(CANCEL_TTL_HOURS));
        log.info("AI 工作流取消请求: taskId={}", taskId);
        Optional<WorkflowTask> optional = taskRepository.findByTaskId(taskId);
        if (optional.isPresent()) {
            WorkflowTask task = optional.get();
            if (task.getStatus() == TaskStatus.PENDING || task.getStatus() == TaskStatus.RUNNING
                    || task.getStatus() == TaskStatus.RETRYING || task.getStatus() == TaskStatus.WAITING_INPUT) {
                return true;
            }
        }
        return false;
    }

    public boolean isCancelled(String taskId) {
        return runtimeStateStore.isCancellationRequested(taskId);
    }

    private WorkflowTask createTask(AgentWorkflowRequest request, String taskId) {
        WorkflowTask task = new WorkflowTask();
        task.setTaskId(taskId);
        task.setMode(AGENT_MODE);
        task.setStatus(TaskStatus.PENDING);
        task.setExecutionStage(AgentExecutionStage.PERCEIVING);
        task.setOriginalTask(request.getTask());
        task.setBusinessPrompt(request.getBusinessPrompt());
        task.setMaxSteps(request.getMaxSteps());
        task.setEnableTools(request.isEnableTools());
        task.setUserId(com.kset.agent.spi.AgentAuthContext.getUserId());
        task.setSessionId(request.getSessionId());
        task.setMessageId(request.getMessageId());
        task.setProjectId(request.getProjectId());
        task.setFinished(false);
        task.setCurrentStep(0);
        task.setRequestSnapshot(writeRequestSnapshot(request));
        task.setCreatedAt(new Date());
        task.setUpdatedAt(new Date());
        return task;
    }

    private String writeRequestSnapshot(AgentWorkflowRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalStateException("保存工作流请求快照失败", e);
        }
    }

    private void saveCheckpoint(WorkflowTask task, AgentWorkflowRequest request,
                                AgentWorkflowResult.Step resultStep, long fencingToken) {
        synchronized (task) {
            WorkflowStep step = toWorkflowStep(task.getTaskId(), resultStep);
            boolean advanceTask = step.getStatus() != StepStatus.RUNNING
                    && step.getStepIndex() >= task.getCurrentStep();
            if (advanceTask) {
                task.setCurrentStep(step.getStepIndex());
                task.setUpdatedAt(new Date());
            }
            refreshTaskSnapshot(task, request);
            taskRepository.saveCheckpoint(step, task, advanceTask, ownerId, fencingToken);
        }
    }

    private AgentWorkflowResult pauseForCriticalExtensionFailure(
            WorkflowTask task, AgentWorkflowResult result,
            CriticalWorkflowExtensionException extensionError) {
        log.error("关键工作流扩展失败，任务暂停等待恢复: code={}, taskId={}, error={}",
                extensionError.extensionCode(), task.getTaskId(), extensionError.getCause().getMessage());
        task.setFinished(false);
        updateTaskStatus(task, TaskStatus.PAUSED, extensionError.getCause().getMessage());
        result.setFinished(false);
        result.setRequiresConfirmation(false);
        result.setTaskStatus(TaskStatus.PAUSED.name());
        result.setExecutionStage(AgentExecutionStage.PERSISTING_MEMORY.code());
        return result;
    }

    private void refreshTaskSnapshot(WorkflowTask task, AgentWorkflowRequest request) {
        task.setRequestSnapshot(writeRequestSnapshot(request));
        task.setExecutionSnapshot(writeExecutionSnapshot(request.getExecutionSnapshot()));
    }

    private void transitionExecutionStage(WorkflowTask task, AgentExecutionStage next) {
        synchronized (task) {
            AgentExecutionLifecycle.requireTransition(task.getExecutionStage(), next);
            if (task.getExecutionStage() == next) {
                return;
            }
            task.setExecutionStage(next);
            task.setUpdatedAt(new Date());
            taskRepository.updateStatus(task, ownerId, task.getFencingToken());
        }
    }

    private void updateRuntimeTaskStatus(WorkflowTask task, TaskStatus status) {
        if (status != TaskStatus.RUNNING && status != TaskStatus.RETRYING) {
            throw new IllegalArgumentException("执行期只允许切换 RUNNING/RETRYING");
        }
        synchronized (task) {
            if (task.getStatus() != status) {
                updateTaskStatus(task, status, null);
            }
        }
    }

    private AgentExecutionSnapshot readExecutionSnapshot(WorkflowTask task) {
        if (task.getExecutionSnapshot() == null || task.getExecutionSnapshot().isBlank()) {
            AgentExecutionSnapshot snapshot = new AgentExecutionSnapshot();
            snapshot.setStepNo(task.getCurrentStep());
            snapshot.setExecutionStage(task.getExecutionStage());
            return snapshot;
        }
        try {
            AgentExecutionSnapshot snapshot = objectMapper.readValue(
                    task.getExecutionSnapshot(), AgentExecutionSnapshot.class);
            if (task.getExecutionStage() != null) {
                snapshot.setExecutionStage(task.getExecutionStage());
            }
            return snapshot;
        } catch (Exception e) {
            throw new IllegalStateException("读取 Agent 执行恢复快照失败", e);
        }
    }

    private String writeExecutionSnapshot(AgentExecutionSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("保存 Agent 执行恢复快照失败", e);
        }
    }

    private void updateTaskStatus(WorkflowTask task, TaskStatus status, String errorMsg) {
        task.setStatus(status);
        task.setUpdatedAt(new Date());
        if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED
                || status == TaskStatus.CANCELLED || status == TaskStatus.TIMEOUT) {
            task.setCompletedAt(new Date());
        }
        taskRepository.updateStatus(task, ownerId, task.getFencingToken());
    }

    private long claimExecution(String taskId) {
        long fencingToken = taskRepository.claimExecution(taskId, ownerId, leaseSeconds);
        if (fencingToken < 0) {
            throw new BusinessException("工作流任务已被其他实例执行");
        }
        return fencingToken;
    }

    private ScheduledFuture<?> startLeaseRenewal(String taskId, long fencingToken, AtomicBoolean leaseLost) {
        Duration interval = Duration.ofSeconds(Math.max(5L, leaseSeconds / 3));
        return taskScheduler.scheduleAtFixedRate(() -> {
            try {
                if (taskRepository.renewExecution(taskId, ownerId, fencingToken, leaseSeconds)) {
                    return;
                }
                leaseLost.set(true);
                log.error("工作流执行租约续期失败: taskId={}, fencingToken={}", taskId, fencingToken);
            } catch (RuntimeException renewalError) {
                leaseLost.set(true);
                log.error("工作流执行租约续期异常: taskId={}, fencingToken={}",
                        taskId, fencingToken, renewalError);
            }
        }, Instant.now().plus(interval), interval);
    }

    /**
     * 终态判定：正常结束 → COMPLETED；最后一步为 confirmation → WAITING_INPUT（等待用户确认，非失败）；其余 → FAILED。
     */
    private TaskStatus resolveFinalStatus(AgentWorkflowResult result) {
        if (result.isFinished() && hasNoIncompleteSteps(result)) {
            return TaskStatus.COMPLETED;
        }
        List<AgentWorkflowResult.Step> steps = result.getSteps();
        if (steps != null && !steps.isEmpty()
                && AgentOutputType.CONFIRMATION.matches(steps.get(steps.size() - 1).getType())) {
            return TaskStatus.WAITING_INPUT;
        }
        return TaskStatus.FAILED;
    }

    private boolean hasNoIncompleteSteps(AgentWorkflowResult result) {
        if (result.getSteps() == null) return true;
        return result.getSteps().stream()
                .filter(step -> AgentOutputType.TOOL_CALL.matches(step.getType()))
                .allMatch(step -> StepStatus.COMPLETED.name().equalsIgnoreCase(step.getExecutionStatus())
                        && !Boolean.FALSE.equals(step.getToolSuccess()));
    }

    private AgentWorkflowResult buildFallbackResult(String taskId, String mode, String errorMsg) {
        AgentWorkflowResult result = new AgentWorkflowResult();
        result.setTaskId(taskId);
        result.setMode(mode);
        // 用户文案与内部错误分离：不把异常细节（可能含内部信息）暴露给终端用户
        result.setAnswer("工作流执行失败，请稍后重试");
        result.setFinished(false);
        result.setTotalSteps(0);
        result.setExecutionStage(AgentExecutionStage.PERCEIVING.code());
        return result;
    }

    private static final class CriticalWorkflowExtensionException extends RuntimeException {
        private final String extensionCode;

        private CriticalWorkflowExtensionException(String extensionCode, RuntimeException cause) {
            super(cause);
            this.extensionCode = extensionCode;
        }

        private String extensionCode() {
            return extensionCode;
        }
    }

    /**
     * feature-key: conversation-workflow-recovery, change-id: CWR-20260822-01
     * 保留用于回滚到执行结束后批量落库的旧策略。
     */
    @SuppressWarnings("unused")
    private void saveResultStepsForRollback(String taskId, AgentWorkflowResult result) {
        if (result.getSteps() == null || result.getSteps().isEmpty()) {
            return;
        }
        for (AgentWorkflowResult.Step resultStep : result.getSteps()) {
            Date completedAt = new Date();
            long durationMs = resultStep.getDurationMs() != null
                    ? Math.max(0L, resultStep.getDurationMs()) : 0L;
            WorkflowStep step = new WorkflowStep();
            step.setTaskId(taskId);
            step.setStepIndex(resultStep.getStep());
            step.setStatus(resolveStepStatus(resultStep));
            step.setNodeType(resultStep.getType() != null ? resultStep.getType() : result.getMode());
            step.setInputText(firstNonBlank(resultStep.getDisplayContent(), resultStep.getModelRequest(),
                    resultStep.getActionInput()));
            step.setOutputText(firstNonBlank(resultStep.getDisplayContent(), resultStep.getObservation(),
                    resultStep.getModelOutput()));
            step.setThought(resultStep.getThought());
            step.setAction(resultStep.getAction());
            step.setActionInput(firstNonBlank(resultStep.getToolArguments(), resultStep.getActionInput()));
            step.setObservation(resultStep.getObservation());
            step.setErrorMsg(firstNonBlank(resultStep.getProtocolError(),
                    Boolean.FALSE.equals(resultStep.getToolSuccess()) ? resultStep.getObservation() : null));
            step.setRetryCount(0);
            step.setToolName(resultStep.getToolName());
            step.setExecutionId(resultStep.getExecutionId());
            step.setPlanTaskId(resultStep.getPlanTaskId());
            step.setExecutionType(resultStep.getExecutionType());
            step.setStage(resultStep.getStage());
            step.setDisplayTitle(resultStep.getDisplayTitle());
            step.setDisplayContent(resultStep.getDisplayContent());
            step.setDurationMs(durationMs);
            step.setStartedAt(new Date(completedAt.getTime() - durationMs));
            step.setCompletedAt(completedAt);
            step.setCreatedAt(completedAt);
            taskRepository.saveStep(step);
        }
    }

    private WorkflowStep toWorkflowStep(String taskId, AgentWorkflowResult.Step resultStep) {
        Date completedAt = new Date();
        long durationMs = resultStep.getDurationMs() != null
                ? Math.max(0L, resultStep.getDurationMs()) : 0L;
        WorkflowStep step = new WorkflowStep();
        step.setTaskId(taskId);
        step.setStepIndex(resultStep.getStep());
        step.setStatus(resolveStepStatus(resultStep));
        step.setNodeType(resultStep.getType() != null ? resultStep.getType() : AGENT_MODE);
        step.setInputText(firstNonBlank(resultStep.getModelRequest(), resultStep.getActionInput()));
        step.setOutputText(firstNonBlank(resultStep.getDisplayContent(), resultStep.getObservation(),
                resultStep.getModelOutput()));
        step.setThought(resultStep.getThought());
        step.setAction(resultStep.getAction());
        step.setActionInput(firstNonBlank(resultStep.getToolArguments(), resultStep.getActionInput()));
        step.setObservation(resultStep.getObservation());
        step.setErrorMsg(firstNonBlank(resultStep.getProtocolError(),
                Boolean.FALSE.equals(resultStep.getToolSuccess()) ? resultStep.getObservation() : null));
        step.setRetryCount(0);
        step.setToolName(resultStep.getToolName());
        step.setExecutionId(firstNonBlank(resultStep.getExecutionId(),
                step.getNodeType() + "-" + resultStep.getStep()));
        step.setPlanTaskId(resultStep.getPlanTaskId());
        step.setExecutionType(resultStep.getExecutionType());
        step.setStage(resultStep.getStage());
        step.setExecutionStage(resultStep.getExecutionStage());
        step.setDisplayTitle(resultStep.getDisplayTitle());
        step.setDisplayContent(resultStep.getDisplayContent());
        step.setDurationMs(durationMs);
        step.setStartedAt(new Date(completedAt.getTime() - durationMs));
        step.setCompletedAt(step.getStatus() == StepStatus.RUNNING ? null : completedAt);
        step.setCreatedAt(completedAt);
        return step;
    }

    private StepStatus resolveStepStatus(AgentWorkflowResult.Step step) {
        String executionStatus = step.getExecutionStatus();
        if (StepStatus.RUNNING.name().equalsIgnoreCase(executionStatus)) {
            return StepStatus.RUNNING;
        }
        if (StepStatus.FAILED.name().equalsIgnoreCase(executionStatus) || AgentStepType.ERROR.matches(step.getType())
                || Boolean.FALSE.equals(step.getToolSuccess()) || step.getProtocolError() != null) {
            return StepStatus.FAILED;
        }
        if (StepStatus.TIMEOUT.name().equalsIgnoreCase(executionStatus)) {
            return StepStatus.TIMEOUT;
        }
        if (StepStatus.SKIPPED.name().equalsIgnoreCase(executionStatus)) {
            return StepStatus.SKIPPED;
        }
        return StepStatus.COMPLETED;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private List<WorkflowTaskStatus.Step> toStatusSteps(List<WorkflowStep> steps) {
        return steps.stream()
                .map(this::toStatusStep)
                .toList();
    }

    private WorkflowTaskStatus.Step toStatusStep(WorkflowStep step) {
        WorkflowTaskStatus.Step statusStep = new WorkflowTaskStatus.Step();
        statusStep.setStep(step.getStepIndex());
        statusStep.setStatus(step.getStatus() != null ? step.getStatus().name() : null);
        statusStep.setNodeType(step.getNodeType());
        statusStep.setThought(step.getThought());
        statusStep.setAction(step.getAction());
        statusStep.setActionInput(step.getActionInput());
        statusStep.setObservation(step.getObservation());
        statusStep.setErrorMsg(step.getErrorMsg());
        statusStep.setToolName(step.getToolName());
        statusStep.setDurationMs(step.getDurationMs());
        statusStep.setExecutionId(step.getExecutionId());
        statusStep.setPlanTaskId(step.getPlanTaskId());
        statusStep.setParentExecutionId(step.getParentExecutionId());
        statusStep.setExecutionType(step.getExecutionType());
        statusStep.setStage(step.getStage());
        statusStep.setExecutionStage(step.getExecutionStage());
        statusStep.setDisplayTitle(step.getDisplayTitle());
        statusStep.setDisplayContent(step.getDisplayContent());
        statusStep.setStartedAt(step.getStartedAt());
        statusStep.setCompletedAt(step.getCompletedAt());
        return statusStep;
    }

    private String generateTaskId() {
        return "awf-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
