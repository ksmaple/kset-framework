package com.kset.common.monitor.autoconfigure;

import com.kset.cloud.config.KsetCloudProperties;
import com.kset.common.monitor.config.KsetMonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;


@AutoConfiguration
@ConditionalOnProperty(prefix = "kset.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({KsetMonitorProperties.class, KsetCloudProperties.class})
@Import({
        KsetMonitorFacadeAutoConfiguration.class,
        MonitorAutoConfiguration.class,
        KsetMonitorMybatisAutoConfiguration.class,
        KsetMonitorServletAutoConfiguration.class,
        KsetMonitorDubboAutoConfiguration.class,
        KsetMonitorGatewayAutoConfiguration.class,
        KsetMonitorHttpClientAutoConfiguration.class,
        KsetMonitorThreadPoolAutoConfiguration.class,
        KsetMonitorAsyncAutoConfiguration.class
})
public class KsetMonitorAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KsetMonitorAutoConfiguration.class);

    @Bean
    ApplicationRunner ksetMonitorStartupLogger(KsetMonitorProperties properties) {
        return args -> log.info(
                "[kset-monitor] Full-link monitoring active (backend={}, web={}, dubbo={}, gateway={}, mybatis={}, httpClient={}, redis={}, threadPool={})",
                properties.getBackend(),
                properties.getWeb().isEnabled(),
                properties.getDubbo().isEnabled(),
                properties.getGateway().isEnabled() && properties.getGateway().isTraceEnabled(),
                properties.getMybatis().isEnabled(),
                properties.getHttpClient().isEnabled(),
                properties.getRedis().isEnabled(),
                properties.getThreadPool().isEnabled());
    }
}
