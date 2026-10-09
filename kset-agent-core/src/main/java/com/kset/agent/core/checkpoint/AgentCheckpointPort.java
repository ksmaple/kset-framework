package com.kset.agent.core.checkpoint;

import com.kset.agent.core.api.AgentRequest;
import com.kset.agent.core.loop.AgentRunSnapshot;

/**
 * Thread-safe, host-owned snapshot sink. The host remains responsible for loading snapshots before
 * resume and for applying any ownership, revision or fencing checks required by its storage model.
 */
public interface AgentCheckpointPort {

    /** Saves a snapshot using request attributes only as host-side conditional-write context. */
    void save(AgentRequest request, AgentRunSnapshot snapshot);

    static AgentCheckpointPort noop() {
        return (request, snapshot) -> { };
    }
}
