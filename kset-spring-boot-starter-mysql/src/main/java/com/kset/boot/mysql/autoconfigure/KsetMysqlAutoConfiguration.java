package com.kset.boot.mysql.autoconfigure;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.kset.boot.mysql.handler.KsetMetaObjectHandler;
import com.kset.cloud.config.KsetMysqlProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration(before = MybatisPlusAutoConfiguration.class)
@ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
@ConditionalOnProperty(prefix = "kset.mysql", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetMysqlProperties.class)
@Import({KsetMybatisPlusConfiguration.class, KsetFlywayAutoConfiguration.class})
public class KsetMysqlAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "kset.mysql", name = "auto-fill", havingValue = "true", matchIfMissing = true)
    public KsetMetaObjectHandler ksetMetaObjectHandler() {
        return new KsetMetaObjectHandler();
    }
}
