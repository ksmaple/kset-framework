package com.kset.agent.engine;

import com.kset.agent.workflow.WorkflowRuntimeStateStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** PostgreSQL 权威运行状态实现。 */
@Component
@ConditionalOnProperty(name = "ai.workflow.state.store", havingValue = "database", matchIfMissing = true)
public class JdbcWorkflowRuntimeStateStore implements WorkflowRuntimeStateStore {

    private static final String CONFIRMATION = "CONFIRMATION";
    private static final String CANCELLATION = "CANCELLATION";
    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkflowRuntimeStateStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void saveConfirmation(String taskId, String payload, Duration ttl) {
        upsert(taskId, CONFIRMATION, payload, ttl);
    }

    @Override
    public Optional<String> findConfirmation(String taskId) {
        List<String> values = jdbcTemplate.query("""
                        SELECT state_payload FROM t_workflow_runtime_state
                        WHERE task_id = ? AND state_type = ? AND expire_at > CURRENT_TIMESTAMP
                        """, (rs, rowNum) -> rs.getString(1), taskId, CONFIRMATION);
        return values.stream().findFirst();
    }

    @Override
    public void deleteConfirmation(String taskId) {
        jdbcTemplate.update("DELETE FROM t_workflow_runtime_state WHERE task_id = ? AND state_type = ?",
                taskId, CONFIRMATION);
    }

    @Override
    public void requestCancellation(String taskId, Duration ttl) {
        upsert(taskId, CANCELLATION, "1", ttl);
    }

    @Override
    public boolean isCancellationRequested(String taskId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM t_workflow_runtime_state
                        WHERE task_id = ? AND state_type = ? AND expire_at > CURRENT_TIMESTAMP
                        """, Integer.class, taskId, CANCELLATION);
        return count != null && count > 0;
    }

    private void upsert(String taskId, String type, String payload, Duration ttl) {
        jdbcTemplate.update("""
                        INSERT INTO t_workflow_runtime_state(task_id, state_type, state_payload, expire_at, updated_at)
                        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (task_id, state_type) DO UPDATE
                        SET state_payload = EXCLUDED.state_payload, expire_at = EXCLUDED.expire_at,
                            updated_at = CURRENT_TIMESTAMP
                        """, taskId, type, payload, Timestamp.from(Instant.now().plus(ttl)));
    }
}
