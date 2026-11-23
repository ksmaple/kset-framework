package com.kset.agent.harness;

import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.tool.Toolkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHarnessFactoryTest {
    @TempDir
    Path workspace;

    @Test
    void nativeBuilderAcceptsDefaultsAndReachesHostCustomization() {
        Model model = proxy(Model.class);
        AgentStateStore store = proxy(AgentStateStore.class);
        AtomicBoolean customized = new AtomicBoolean();
        AgentHarnessFactory factory = new AgentHarnessFactory(List.of(builder -> customized.set(true)));
        assertDoesNotThrow(() -> factory.builder(new AgentHarnessSpec(
                "regression", "regression", model, "instructions", new Toolkit(), store, workspace, 1)));
        assertTrue(customized.get());
    }

    private <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, arguments) -> { throw new AssertionError("Unexpected call: " + method.getName()); }));
    }
}
