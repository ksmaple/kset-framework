package com.kset.agent.core.extension.output;

public interface AgentOutputConverter {
    String protocol();

    AgentOutputConversion convert(String raw);
}
