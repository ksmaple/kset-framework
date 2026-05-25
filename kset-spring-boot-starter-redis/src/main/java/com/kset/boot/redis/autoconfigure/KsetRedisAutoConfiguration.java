package com.kset.boot.redis.autoconfigure;

import com.kset.boot.redis.config.KsetRedisTemplateConfiguration;
import com.kset.cloud.config.KsetRedisProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
@ConditionalOnProperty(prefix = "kset.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetRedisProperties.class)
@Import({KsetRedisTemplateConfiguration.class, KsetCacheAutoConfiguration.class})
public class KsetRedisAutoConfiguration {
}
