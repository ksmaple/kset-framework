package com.kset.agent.core.protocol;

import com.kset.agent.core.AgentCoreException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable registry that permits multiple protocol names and versions. */
public final class AgentProtocolRegistry {

    private final Map<String, AgentProtocolCodec> codecs;

    public AgentProtocolRegistry(List<AgentProtocolCodec> codecs) {
        Map<String, AgentProtocolCodec> values = new LinkedHashMap<>();
        for (AgentProtocolCodec codec : codecs == null ? List.<AgentProtocolCodec>of() : codecs) {
            if (codec == null || codec.id() == null || values.putIfAbsent(codec.id().key(), codec) != null) {
                throw new IllegalArgumentException("duplicate or missing protocol codec");
            }
        }
        this.codecs = Map.copyOf(values);
    }

    public AgentProtocolCodec require(AgentProtocolId id) {
        AgentProtocolCodec codec = codecs.get(id.key());
        if (codec == null) {
            throw new AgentCoreException("agent protocol is not registered: " + id.key());
        }
        return codec;
    }

    public List<AgentProtocolId> supportedProtocols() {
        return codecs.values().stream().map(AgentProtocolCodec::id).toList();
    }
}
