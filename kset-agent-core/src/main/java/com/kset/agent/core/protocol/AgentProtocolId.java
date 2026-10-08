package com.kset.agent.core.protocol;

import java.util.Locale;

/** Stable protocol identity used for registry lookup and snapshot compatibility. */
public record AgentProtocolId(String name, String version) {

    public AgentProtocolId {
        if (name == null || name.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("protocol name and version must not be blank");
        }
        name = name.trim().toLowerCase(Locale.ROOT);
        version = version.trim().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return name + ":" + version;
    }
}
