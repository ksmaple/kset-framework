package com.kset.agent.core.port;

import com.kset.agent.core.dto.LlmHealthSnapshotDTO;

public interface LlmHealthPort {
    boolean isDegraded();

    LlmHealthSnapshotDTO snapshot();
}
