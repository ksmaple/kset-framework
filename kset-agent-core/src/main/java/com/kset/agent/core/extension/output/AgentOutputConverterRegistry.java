package com.kset.agent.core.extension.output;

import com.kset.agent.core.AgentCoreException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentOutputConverterRegistry {
    private final Map<String, AgentOutputConverter> converters;

    public AgentOutputConverterRegistry(List<AgentOutputConverter> converters) {
        this.converters = converters.stream().collect(Collectors.toUnmodifiableMap(
                converter -> normalize(converter.protocol()), Function.identity()));
    }

    public AgentOutputConversion convert(String protocol, String raw) {
        AgentOutputConverter converter = converters.get(normalize(protocol));
        if (converter == null) {
            throw new AgentCoreException("未注册 Agent 输出协议转换器: " + protocol);
        }
        return converter.convert(raw);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
