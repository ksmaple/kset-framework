package com.kset.agent.extension.output;

public interface AgentOutputConverter {
    String protocol();

    AgentOutputConversion convert(String raw);
}
