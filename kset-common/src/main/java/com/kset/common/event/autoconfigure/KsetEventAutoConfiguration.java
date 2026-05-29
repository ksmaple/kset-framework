package com.kset.common.event.autoconfigure;

import com.kset.common.event.EventFacade;
import com.kset.common.event.EventHandler;
import com.kset.common.event.spring.SpringEventConsumer;
import com.kset.common.event.spring.SpringEventFacade;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "kset.event", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetEventAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventFacade eventFacade(ApplicationEventPublisher publisher,
                                   ObjectProvider<TaskExecutor> taskExecutorProvider) {
        return new SpringEventFacade(publisher, taskExecutorProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringEventConsumer springEventConsumer(List<EventHandler<?>> handlers) {
        return new SpringEventConsumer(handlers);
    }
}
