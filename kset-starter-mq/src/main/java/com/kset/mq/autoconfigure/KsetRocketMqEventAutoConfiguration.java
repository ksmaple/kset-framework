package com.kset.mq.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.common.event.EventFacade;
import com.kset.common.event.EventHandler;
import com.kset.common.utils.JsonUtil;
import com.kset.mq.event.RocketMqEventConsumer;
import com.kset.mq.event.RocketMqEventFacade;
import com.kset.mq.event.RocketMqEventOperations;
import com.kset.mq.lifecycle.KsetMqEventLifecycle;
import org.apache.rocketmq.client.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.client.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.apache.rocketmq.client.support.RocketMQListenerContainer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.List;

@AutoConfiguration(
        after = RocketMQAutoConfiguration.class,
        beforeName = "com.kset.common.event.autoconfigure.KsetEventAutoConfiguration"
)
@ConditionalOnClass(RocketMQClientTemplate.class)
public class KsetRocketMqEventAutoConfiguration {

    @Bean
    @ConditionalOnBean(RocketMQClientTemplate.class)
    @ConditionalOnMissingBean(EventFacade.class)
    public RocketMqEventFacade rocketMqEventFacade(RocketMQClientTemplate rocketMqTemplate,
                                                   ObjectProvider<ObjectMapper> objectMapperProvider,
                                                   RocketMQProperties rocketMqProperties,
                                                   Environment environment) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(JsonUtil::mapper);
        return new RocketMqEventFacade(rocketMqTemplate, objectMapper, rocketMqProperties, environment);
    }

    @Bean
    @ConditionalOnBean(RocketMqEventFacade.class)
    @ConditionalOnMissingBean(RocketMqEventOperations.class)
    public RocketMqEventOperations rocketMqEventOperations(RocketMqEventFacade eventFacade) {
        return eventFacade;
    }

    @Bean
    @ConditionalOnClass(RocketMQListenerContainer.class)
    @ConditionalOnProperty(prefix = "kset.lifecycle", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public KsetMqEventLifecycle ksetMqEventLifecycle(ObjectProvider<RocketMQListenerContainer> containers) {
        return new KsetMqEventLifecycle(containers);
    }

    @Bean
    @ConditionalOnBean({RocketMQClientTemplate.class, EventHandler.class})
    @ConditionalOnMissingBean(RocketMqEventConsumer.class)
    public RocketMqEventConsumer rocketMqEventConsumer(List<EventHandler<?>> handlers,
                                                       ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(JsonUtil::mapper);
        return new RocketMqEventConsumer(handlers, objectMapper);
    }
}
