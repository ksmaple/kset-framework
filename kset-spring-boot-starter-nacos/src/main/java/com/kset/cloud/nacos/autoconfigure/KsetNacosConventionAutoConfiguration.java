package com.kset.cloud.nacos.autoconfigure;

import com.kset.cloud.config.KsetCloudProperties;
import com.kset.cloud.nacos.NacosConfigConvention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

@AutoConfiguration
@ConditionalOnClass(name = "com.alibaba.cloud.nacos.NacosConfigAutoConfiguration")
@EnableConfigurationProperties(KsetCloudProperties.class)
public class KsetNacosConventionAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KsetNacosConventionAutoConfiguration.class);

    @Bean
    public KsetNacosConventionInitializer ksetNacosConventionInitializer(Environment environment,
                                                                         KsetCloudProperties properties,
                                                                         NacosConfigConvention convention) {
        return new KsetNacosConventionInitializer(environment, properties, convention);
    }

    static class KsetNacosConventionInitializer {

        KsetNacosConventionInitializer(Environment environment,
                                       KsetCloudProperties properties,
                                       NacosConfigConvention convention) {
            if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
                return;
            }
            Map<String, Object> defaults = new LinkedHashMap<>();
            putIfMissing(environment, defaults, "spring.cloud.nacos.config.namespace", convention.namespace());
            putIfMissing(environment, defaults, "spring.cloud.nacos.discovery.namespace", convention.namespace());
            putIfMissing(environment, defaults, "spring.cloud.nacos.config.group", convention.group());
            putIfMissing(environment, defaults, "spring.cloud.nacos.discovery.group", convention.group());
            putIfMissing(environment, defaults, "spring.cloud.nacos.config.file-extension", "yaml");

            if (!defaults.isEmpty()) {
                configurableEnvironment.getPropertySources()
                        .addLast(new MapPropertySource("ksetNacosConvention", defaults));
            }
            log.info("KSet Nacos convention applied: group={}, namespace={}, commonConfig={}",
                    convention.group(), convention.namespace(), convention.commonConfigDataId());
        }

        private static void putIfMissing(Environment environment, Map<String, Object> defaults,
                                         String key, String value) {
            if (environment.getProperty(key) == null && value != null) {
                defaults.put(key, value);
            }
        }
    }
}
