package com.kset.redis.monitor;

import com.kset.monitor.facade.MonitorTypes;
import com.kset.monitor.interceptor.FrameworkInterceptor;
import com.kset.monitor.interceptor.InvocationContext;
import com.kset.monitor.interceptor.MonitorInterceptorRegistry;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Redis 监控插件：注册 {@link FrameworkInterceptor}，供 Redis 操作侧发送 {@link InvocationContext}。
 */
@AutoConfiguration
@ConditionalOnClass(name = "com.kset.monitor.Monitor")
@ConditionalOnProperty(prefix = "kset.monitor.plugin.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisMonitorPluginAutoConfiguration {

    @Bean
    ApplicationRunner redisMonitorPluginRegistrar() {
        return args -> MonitorInterceptorRegistry.register(new RedisMonitorPlugin());
    }

    static final class RedisMonitorPlugin implements FrameworkInterceptor {

        @Override
        public void before(InvocationContext context) {
            if (!MonitorTypes.CACHE.equals(context.getType())) {
                return;
            }
            context.setAttribute("startNanos", String.valueOf(System.nanoTime()));
        }

        @Override
        public void after(InvocationContext context, Throwable error) {
            if (!MonitorTypes.CACHE.equals(context.getType())) {
                return;
            }
        }
    }
}
