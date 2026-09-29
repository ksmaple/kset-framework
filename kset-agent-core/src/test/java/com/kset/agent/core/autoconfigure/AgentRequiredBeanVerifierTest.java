package com.kset.agent.core.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRequiredBeanVerifierTest {

    private ConfigurableListableBeanFactory factoryWithAllMissing() {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(any(Class.class), anyBoolean(), anyBoolean()))
                .thenReturn(new String[0]);
        when(beanFactory.containsBean(anyString())).thenReturn(false);
        return beanFactory;
    }

    private ConfigurableListableBeanFactory factoryWithAllPresent() {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(any(Class.class), anyBoolean(), anyBoolean()))
                .thenReturn(new String[] {"bean"});
        when(beanFactory.containsBean(AgentRequiredBeanVerifier.TOOL_EXECUTOR_BEAN_NAME)).thenReturn(true);
        return beanFactory;
    }

    @Test
    void throwsWhenAllRequiredBeansMissing() {
        AgentRequiredBeanVerifier verifier = new AgentRequiredBeanVerifier();
        verifier.setEnvironment(new MockEnvironment());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> verifier.postProcessBeanFactory(factoryWithAllMissing()));
        assertTrue(ex.getMessage().contains("AgentChatModelPort"));
        assertTrue(ex.getMessage().contains("AiModelProvider"));
        assertTrue(ex.getMessage().contains("WorkflowTaskRepository"));
        assertTrue(ex.getMessage().contains("WorkflowRuntimeStateStore"));
        assertTrue(ex.getMessage().contains(AgentRequiredBeanVerifier.TOOL_EXECUTOR_BEAN_NAME));
        assertTrue(ex.getMessage().contains(AgentRequiredBeanVerifier.CHECK_ENABLED_PROPERTY));
    }

    @Test
    void passesWhenAllRequiredBeansPresent() {
        AgentRequiredBeanVerifier verifier = new AgentRequiredBeanVerifier();
        verifier.setEnvironment(new MockEnvironment());

        assertDoesNotThrow(() -> verifier.postProcessBeanFactory(factoryWithAllPresent()));
    }

    @Test
    void checkCanBeDisabledViaEnvironmentProperty() {
        AgentRequiredBeanVerifier verifier = new AgentRequiredBeanVerifier();
        verifier.setEnvironment(new MockEnvironment()
                .withProperty(AgentRequiredBeanVerifier.CHECK_ENABLED_PROPERTY, "false"));

        assertDoesNotThrow(() -> verifier.postProcessBeanFactory(factoryWithAllMissing()));
    }

    @Test
    void nullEnvironmentKeepsCheckEnabled() {
        AgentRequiredBeanVerifier verifier = new AgentRequiredBeanVerifier();

        assertThrows(IllegalStateException.class,
                () -> verifier.postProcessBeanFactory(factoryWithAllMissing()));
    }

    @Test
    void throwsWhenOnlyToolExecutorMissing() {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(any(Class.class), anyBoolean(), anyBoolean()))
                .thenReturn(new String[] {"bean"});
        when(beanFactory.containsBean(anyString())).thenReturn(false);

        AgentRequiredBeanVerifier verifier = new AgentRequiredBeanVerifier();
        verifier.setEnvironment(new MockEnvironment());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> verifier.postProcessBeanFactory(beanFactory));
        assertTrue(ex.getMessage().contains(AgentRequiredBeanVerifier.TOOL_EXECUTOR_BEAN_NAME));
    }
}
