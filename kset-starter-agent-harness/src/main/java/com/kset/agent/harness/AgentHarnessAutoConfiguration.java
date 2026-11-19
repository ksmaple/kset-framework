package com.kset.agent.harness;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AgentHarnessProperties.class)
@ConditionalOnProperty(prefix = "kset.agent.harness", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentHarnessAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    AgentHarnessFactory agentHarnessFactory(ObjectProvider<AgentHarnessCustomizer> customizers) {
        return new AgentHarnessFactory(customizers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    AgentHarnessToolkitFactory agentHarnessToolkitFactory() {
        return new AgentHarnessToolkitFactory();
    }
}
