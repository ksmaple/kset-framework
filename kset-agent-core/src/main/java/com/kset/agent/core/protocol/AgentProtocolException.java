package com.kset.agent.core.protocol;

import com.kset.agent.core.AgentCoreException;

/** Recoverable model-protocol violation. */
public class AgentProtocolException extends AgentCoreException {

    private final String code;

    public AgentProtocolException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AgentProtocolException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
