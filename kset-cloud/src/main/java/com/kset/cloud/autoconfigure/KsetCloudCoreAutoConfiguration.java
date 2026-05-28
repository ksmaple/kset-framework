package com.kset.cloud.autoconfigure;

import com.kset.cloud.config.KsetCloudProperties;
import com.kset.cloud.nacos.NacosConfigConvention;
import com.kset.cloud.spi.DefaultGrayTagResolver;
import com.kset.cloud.spi.GrayTagResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(KsetCloudProperties.class)
public class KsetCloudCoreAutoConfiguration {

    @Bean
    public NacosConfigConvention nacosConfigConvention(KsetCloudProperties properties,
                                                       org.springframework.core.env.Environment environment) {
        return new NacosConfigConvention(properties, environment);
    }

    @Bean
    @ConditionalOnMissingBean
    public GrayTagResolver grayTagResolver(KsetCloudProperties properties) {
        return new DefaultGrayTagResolver(properties);
    }
}
