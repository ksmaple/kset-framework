package com.kset.common.monitor.autoconfigure;

import com.dianping.cat.Cat;
import com.kset.common.monitor.backend.CatBackend;
import com.kset.common.monitor.backend.LogBackend;
import com.kset.common.monitor.backend.MonitorBackend;
import com.kset.common.monitor.config.KsetMonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class MonitorBackendConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MonitorBackendConfiguration.class);

    /**
     * CAT 后端：仅在显式配置 {@code kset.monitor.backend=cat} 时启用。
     */
    @Bean
    @ConditionalOnClass(Cat.class)
    @ConditionalOnProperty(prefix = "kset.monitor", name = "backend", havingValue = "cat")
    @ConditionalOnMissingBean
    public MonitorBackend catMonitorBackend(KsetMonitorProperties properties, Environment environment) {
        KsetMonitorProperties.Cat catProperties = properties.getCat();
        if (catProperties.isInitialize()) {
            String domain = resolveCatDomain(environment, catProperties);
            if (domain != null && !domain.isBlank()) {
                Cat.initializeByDomain(domain);
            } else {
                Cat.initialize(new String[0]);
            }
        }
        log.info("kset.monitor.backend=cat, using CAT monitor backend");
        return new CatBackend();
    }

    /**
     * 默认监控后端：本地 SLF4J 日志输出；未显式配置 CAT 时不会初始化 CAT 客户端。
     */
    @Bean
    @ConditionalOnMissingBean
    public MonitorBackend logMonitorBackend(KsetMonitorProperties properties) {
        String backend = normalizeBackend(properties.getBackend());
        if (!"log".equals(backend)) {
            log.warn("kset.monitor.backend={} is not supported; using local LogBackend", backend);
        }
        return new LogBackend();
    }

    private static String normalizeBackend(String backend) {
        if (backend == null || backend.isBlank()) {
            return "log";
        }
        return backend.trim().toLowerCase();
    }

    private static String resolveCatDomain(Environment environment, KsetMonitorProperties.Cat catProperties) {
        String applicationName = environment.getProperty("spring.application.name");
        if (applicationName != null && !applicationName.isBlank()) {
            return applicationName.trim();
        }
        String configuredDomain = catProperties.getDomain();
        if (configuredDomain != null && !configuredDomain.isBlank()) {
            return configuredDomain.trim();
        }
        return null;
    }
}
