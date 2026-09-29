package com.kset.agent.core.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryToolRegistryTest {

    private InMemoryToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryToolRegistry();
    }

    private static ToolDefinition tool(String name, String category) {
        return new ToolDefinition(name, "desc-" + name, category, "test", List.of(), args -> null);
    }

    private static ToolDefinition disabledTool(String name, String category) {
        ToolDefinition tool = tool(name, category);
        tool.setEnabled(false);
        return tool;
    }

    @Test
    void registerAndGetReturnsTool() {
        ToolDefinition tool = tool("search", "query");
        registry.register(tool);
        assertSame(tool, registry.get("search"));
    }

    @Test
    void registerNullOrNullNameIsIgnored() {
        registry.register(null);
        registry.register(tool(null, "query"));
        assertTrue(registry.listAll().isEmpty());
    }

    @Test
    void getReturnsNullForUnknownOrNullName() {
        assertNull(registry.get("missing"));
        assertNull(registry.get(null));
    }

    @Test
    void registerOverwritesSameName() {
        ToolDefinition first = tool("search", "query");
        ToolDefinition second = tool("search", "write");
        registry.register(first);
        registry.register(second);
        assertSame(second, registry.get("search"));
        assertEquals(1, registry.listAll().size());
    }

    @Test
    void unregisterRemovesTool() {
        registry.register(tool("search", "query"));
        registry.unregister("search");
        assertNull(registry.get("search"));
        assertTrue(registry.listAll().isEmpty());
    }

    @Test
    void unregisterNullOrUnknownNameIsNoOp() {
        registry.register(tool("search", "query"));
        registry.unregister(null);
        registry.unregister("missing");
        assertEquals(1, registry.listAll().size());
    }

    @Test
    void listAllReturnsOnlyEnabledTools() {
        registry.register(tool("enabledTool", "query"));
        registry.register(disabledTool("disabledTool", "query"));
        List<ToolDefinition> all = registry.listAll();
        assertEquals(1, all.size());
        assertEquals("enabledTool", all.get(0).getName());
    }

    @Test
    void listByCategoryFiltersByCategoryAndEnabled() {
        registry.register(tool("queryTool", "query"));
        registry.register(tool("writeTool", "write"));
        registry.register(disabledTool("disabledQueryTool", "query"));

        List<ToolDefinition> queryTools = registry.listByCategory("query");
        assertEquals(1, queryTools.size());
        assertEquals("queryTool", queryTools.get(0).getName());

        List<ToolDefinition> writeTools = registry.listByCategory("write");
        assertEquals(1, writeTools.size());
        assertEquals("writeTool", writeTools.get(0).getName());

        assertTrue(registry.listByCategory("missing").isEmpty());
    }

    @Test
    void listByCategoryWithNullCategoryReturnsAllEnabled() {
        registry.register(tool("queryTool", "query"));
        registry.register(tool("writeTool", "write"));
        registry.register(disabledTool("disabledTool", "query"));
        assertEquals(2, registry.listByCategory(null).size());
    }
}
