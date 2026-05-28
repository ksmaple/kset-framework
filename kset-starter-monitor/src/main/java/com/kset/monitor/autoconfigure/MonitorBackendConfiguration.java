package com.kset.monitor.autoconfigure;

import com.kset.monitor.backend.CatBackend;
import com.kset.monitor.backend.LogBackend;
import com.kset.monitor.backend.MonitorBackend;
import com.kset.monitor.backend.PrometheusBackend;
import com.kset.monitor.backend.SkywalkingBackend;
import com.kset.monitor.config.KsetMonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitorBackendConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MonitorBackendConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public MonitorBackend monitorBackend(KsetMonitorProperties properties) {
        String backend = properties.getBackend() != null ? properties.getBackend().trim().toLowerCase() : "log";
        long warnMs = properties.getSlowLog().getTransactionWarnMs();
        MonitorBackend selected = switch (backend) {
            case "cat" -> {
                log.warn("kset.monitor.backend=cat is placeholder only; using LogBackend until Phase 2");
                yield new CatBackend();
            }
            case "skywalking" -> {
                log.warn("kset.monitor.backend=skywalking is placeholder only; using LogBackend until Phase 2");
                yield new SkywalkingBackend();
            }
            case "prometheus" -> {
                log.warn("kset.monitor.backend=prometheus is placeholder only; using LogBackend until Phase 2");
                yield new PrometheusBackend();
            }
            default -> new LogBackend(warnMs);
        };
        if (!selected.isEnabled()) {
            return new LogBackend(warnMs);
        }
        return selected;
    }
}
