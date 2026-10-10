package com.kset.agent.core.protocol;

import com.kset.agent.core.AgentCoreException;
import com.kset.agent.core.AgentErrorCode;

/** Recoverable model-protocol violation. */
public class AgentProtocolException extends AgentCoreException {

    private final String protocolErrorCode;

    public AgentProtocolException(String protocolErrorCode, String errorMessage) {
        this(protocolErrorCode, errorMessage, null);
    }

    public AgentProtocolException(
            String protocolErrorCode, String errorMessage, Throwable cause) {
        super(AgentErrorCode.PROTOCOL_INVALID, normalizeMessage(errorMessage), cause);
        if (protocolErrorCode == null || protocolErrorCode.isBlank()) {
            throw new IllegalArgumentException("protocol error code must not be blank");
        }
        this.protocolErrorCode = protocolErrorCode.trim();
    }

    public String protocolErrorCode() {
        return protocolErrorCode;
    }

    private static String normalizeMessage(String errorMessage) {
        return errorMessage == null || errorMessage.isBlank()
                ? "protocol response is invalid" : errorMessage;
    }
}
