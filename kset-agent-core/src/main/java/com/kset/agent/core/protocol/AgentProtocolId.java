package com.kset.agent.core.protocol;

import java.util.Locale;

/** Stable protocolName/protocolVersion identity used for lookup and snapshot compatibility. */
public record AgentProtocolId(String protocolName, String protocolVersion) {

    public AgentProtocolId {
        if (protocolName == null || protocolName.isBlank()
                || protocolVersion == null || protocolVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "protocolName and protocolVersion must not be blank");
        }
        protocolName = protocolName.trim().toLowerCase(Locale.ROOT);
        protocolVersion = protocolVersion.trim().toLowerCase(Locale.ROOT);
    }

    /** Human-readable label only; structural identity is the record's two components. */
    public String key() {
        return protocolName + ":" + protocolVersion;
    }
}
