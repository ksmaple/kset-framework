package com.kset.agent.spi;

/**
 * Agent 运行时配置读取端口（如 top-k、开关、上限等动态配置）。
 *
 * <p>默认实现基于 Spring Environment 读取；宿主应用可覆盖以接入配置中心或数据库配置。
 */
public interface AgentRuntimeConfigPort {

    int intValue(String key, int defaultValue);

    boolean boolValue(String key, boolean defaultValue);

    String stringValue(String key, String defaultValue);
}
