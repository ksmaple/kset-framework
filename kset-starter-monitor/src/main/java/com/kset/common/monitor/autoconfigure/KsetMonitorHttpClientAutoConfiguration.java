package com.kset.common.monitor.autoconfigure;

import com.kset.common.monitor.config.KsetMonitorProperties;
import com.kset.common.utils.http.KsetHttp;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class KsetMonitorHttpClientAutoConfiguration {

    @Bean
    ApplicationRunner ksetMonitorHttpClientConfigurer(KsetMonitorProperties properties) {
        return args -> KsetHttp.setMonitorEnabled(
                properties.isEnabled() && properties.getHttpClient().isEnabled());
    }
}
