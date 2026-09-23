package com.kset.agent.engine;

import com.kset.agent.workflow.WorkflowRuntimeStateStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Redis 兼容实现。 */
@Component
@ConditionalOnProperty(name = "ai.workflow.state.store", havingValue = "redis")
public class RedisWorkflowRuntimeStateStore implements WorkflowRuntimeStateStore {

    private static final String CONFIRMATION_PREFIX = "workflow:confirmation:";
    private static final String CANCEL_PREFIX = "workflow:cancel:";
    private final StringRedisTemplate redisTemplate;

    public RedisWorkflowRuntimeStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveConfirmation(String taskId, String payload, Duration ttl) {
        redisTemplate.opsForValue().set(CONFIRMATION_PREFIX + taskId, payload, ttl);
    }

    @Override
    public Optional<String> findConfirmation(String taskId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(CONFIRMATION_PREFIX + taskId));
    }

    @Override
    public void deleteConfirmation(String taskId) {
        redisTemplate.delete(CONFIRMATION_PREFIX + taskId);
    }

    @Override
    public void requestCancellation(String taskId, Duration ttl) {
        redisTemplate.opsForValue().set(CANCEL_PREFIX + taskId, "1", ttl);
    }

    @Override
    public boolean isCancellationRequested(String taskId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CANCEL_PREFIX + taskId));
    }
}
