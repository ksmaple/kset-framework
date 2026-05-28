package com.kset.monitor.autoconfigure;

import com.kset.cloud.config.KsetCloudProperties;
import com.kset.monitor.config.KsetMonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 全链路监控入口：引入本 starter 后按 classpath 自动装配 Servlet / Dubbo / Gateway / 线程池链路能力。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kset.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({KsetMonitorProperties.class, KsetCloudProperties.class})
@Import({
        KsetMonitorFacadeAutoConfiguration.class,
        MonitorAutoConfiguration.class,
        KsetMonitorServletAutoConfiguration.class,
        KsetMonitorDubboAutoConfiguration.class,
        KsetMonitorGatewayAutoConfiguration.class,
        KsetMonitorThreadPoolAutoConfiguration.class,
        KsetMonitorAsyncAutoConfiguration.class
})
public class KsetMonitorAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KsetMonitorAutoConfiguration.class);

    @Bean
    ApplicationRunner ksetMonitorStartupLogger(KsetMonitorProperties properties) {
        return args -> log.info(
                "[kset-monitor] Full-link monitoring active (servlet.trace={}, dubbo={}, gateway.trace={}, threadPool.trace={})",
                properties.getServlet().isTraceEnabled(),
                properties.getDubbo().isEnabled(),
                properties.getGateway().isTraceEnabled(),
                properties.getThreadPool().isTracePropagationEnabled());
    }
}
