package com.kset.common.event.autoconfigure;

import com.kset.common.event.EventFacade;
import com.kset.common.event.EventHandler;
import com.kset.common.event.config.KsetEventProperties;
import com.kset.common.event.spring.SpringEventConsumer;
import com.kset.common.event.spring.SpringEventFacade;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

@AutoConfiguration
@ConditionalOnProperty(prefix = "kset.event", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetEventProperties.class)
public class KsetEventAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventFacade eventFacade(ApplicationEventPublisher publisher,
                                   ObjectProvider<TaskExecutor> taskExecutorProvider,
                                   ApplicationContext applicationContext,
                                   KsetEventProperties properties) {
        return new SpringEventFacade(publisher, resolveTaskExecutor(taskExecutorProvider, applicationContext, properties));
    }

    @Bean
    @ConditionalOnMissingBean
    public SpringEventConsumer springEventConsumer(List<EventHandler<?>> handlers) {
        return new SpringEventConsumer(handlers);
    }

    private static TaskExecutor resolveTaskExecutor(ObjectProvider<TaskExecutor> provider,
                                                    ApplicationContext applicationContext,
                                                    KsetEventProperties properties) {
        String executorBeanName = properties.getExecutorBeanName();
        if (executorBeanName != null && !executorBeanName.isBlank()) {
            return applicationContext.getBean(executorBeanName, TaskExecutor.class);
        }
        try {
            return provider.getIfAvailable();
        } catch (NoUniqueBeanDefinitionException ignored) {
            return null;
        }
    }
}
