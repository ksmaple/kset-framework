package com.kset.agent.core.protocol;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;

/** Recoverable model-protocol violation. */
public class AgentProtocolException extends AgentCoreException {

    private final String protocolCode;

    public AgentProtocolException(String protocolCode, String message) {
        this(protocolCode, message, null);
    }

    public AgentProtocolException(String protocolCode, String message, Throwable cause) {
        super(AgentErrorCode.PROTOCOL_INVALID, message, cause);
        if (protocolCode == null || protocolCode.isBlank()) {
            throw new IllegalArgumentException("protocol error code must not be blank");
        }
        this.protocolCode = protocolCode.trim();
    }

    public String protocolCode() {
        return protocolCode;
    }
}
