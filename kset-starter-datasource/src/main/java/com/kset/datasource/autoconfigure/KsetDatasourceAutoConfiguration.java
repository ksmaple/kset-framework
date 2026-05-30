package com.kset.datasource.autoconfigure;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.kset.cloud.config.KsetDatasourceProperties;
import com.kset.datasource.handler.KsetMetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(before = MybatisPlusAutoConfiguration.class)
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnProperty(prefix = "kset.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetDatasourceProperties.class)
@Import({KsetFlywayAutoConfiguration.class})
public class KsetDatasourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    @ConditionalOnProperty(prefix = "kset.datasource", name = "auto-fill", havingValue = "true", matchIfMissing = true)
    public KsetMetaObjectHandler ksetMetaObjectHandler() {
        return new KsetMetaObjectHandler();
    }
}
