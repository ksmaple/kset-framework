package com.kset.agent.core.protocol;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable registry that freezes each codec's structural protocol identity at construction. */
public final class AgentProtocolRegistry {

    private final Map<AgentProtocolId, AgentProtocolCodec> codecs;
    private final List<AgentProtocolId> supportedProtocols;

    public AgentProtocolRegistry(List<AgentProtocolCodec> codecs) {
        Map<AgentProtocolId, AgentProtocolCodec> values = new LinkedHashMap<>();
        for (AgentProtocolCodec codec : codecs == null ? List.<AgentProtocolCodec>of() : codecs) {
            AgentProtocolId id = codec == null ? null : codec.id();
            if (id == null || values.putIfAbsent(id, codec) != null) {
                throw new AgentCoreException(AgentErrorCode.INVALID_CONFIGURATION,
                        "duplicate or missing protocol codec");
            }
        }
        this.codecs = Map.copyOf(values);
        this.supportedProtocols = List.copyOf(values.keySet());
    }

    public AgentProtocolCodec require(AgentProtocolId id) {
        if (id == null) {
            throw new AgentCoreException(AgentErrorCode.PROTOCOL_NOT_REGISTERED,
                    "agent protocol is not registered: null");
        }
        AgentProtocolCodec codec = codecs.get(id);
        if (codec == null) {
            throw new AgentCoreException(AgentErrorCode.PROTOCOL_NOT_REGISTERED,
                    "agent protocol is not registered: " + id.key());
        }
        return codec;
    }

    public List<AgentProtocolId> supportedProtocols() {
        return supportedProtocols;
    }
}
