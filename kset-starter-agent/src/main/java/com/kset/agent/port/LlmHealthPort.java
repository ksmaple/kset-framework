package com.kset.agent.port;

import com.kset.agent.dto.LlmHealthSnapshotDTO;

public interface LlmHealthPort {
    boolean isDegraded();

    LlmHealthSnapshotDTO snapshot();
}
