package com.kset.agent.core.checkpoint;

import com.kset.agent.core.loop.AgentRunSnapshot;

import java.util.Optional;

/** Host-owned durable state boundary. */
public interface AgentCheckpointPort {

    void save(AgentRunSnapshot snapshot);

    Optional<AgentRunSnapshot> find(String runId);

    static AgentCheckpointPort noop() {
        return new AgentCheckpointPort() {
            @Override
            public void save(AgentRunSnapshot snapshot) {
            }

            @Override
            public Optional<AgentRunSnapshot> find(String runId) {
                return Optional.empty();
            }
        };
    }
}
