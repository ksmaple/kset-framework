package com.kset.redis.autoconfigure;

import com.kset.cloud.config.KsetRedisProperties;
import com.kset.redis.codec.KsetFastjsonRedisSerializer;
import com.kset.redis.config.KsetRedisConnectionFactoryBuilder;
import com.kset.redis.config.KsetRedisConnectionCustomizer;
import com.kset.redis.config.KsetRedisSerializerConfiguration;
import com.kset.redis.config.KsetRedisTemplateFactory;
import com.kset.redis.core.KsetRedisRegistry;
import com.kset.redis.core.KsetRedisService;
import com.kset.redis.core.KsetRedisStreamSettings;
import com.kset.redis.core.KsetRedisTtlPolicy;
import com.kset.redis.support.KsetRedisNamedSources;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 声明式多 Redis 数据源（{@code kset.redis.sources.*}）。
 */
@Configuration
@EnableConfigurationProperties(KsetRedisProperties.class)
@ConditionalOnProperty(prefix = "kset.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetRedisMultiSourceConfiguration {

    public static String namedServiceBeanName(String sourceName) {
        return sourceName + "KsetRedisService";
    }

    @Bean
    public KsetRedisNamedSources ksetRedisNamedSources(KsetRedisProperties properties,
                                                       KsetRedisTtlPolicy ttlPolicy,
                                                       KsetRedisStreamSettings streamSettings,
                                                       ConfigurableListableBeanFactory beanFactory,
                                                       Environment environment,
                                                       ObjectProvider<KsetRedisConnectionCustomizer> connectionCustomizers,
                                                       @Qualifier(KsetRedisSerializerConfiguration.BEAN_NAME)
                                                       KsetFastjsonRedisSerializer valueSerializer) {
        Map<String, KsetRedisProperties.RedisSourceProperties> sources = properties.getSources();
        if (sources == null || sources.isEmpty()) {
            return KsetRedisNamedSources.empty();
        }
        Map<String, KsetRedisService> services = new LinkedHashMap<>();
        List<LettuceConnectionFactory> connectionFactories = new ArrayList<>();
        for (Map.Entry<String, KsetRedisProperties.RedisSourceProperties> entry : sources.entrySet()) {
            String name = entry.getKey();
            KsetRedisProperties.RedisSourceProperties source = entry.getValue();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            if (KsetRedisRegistry.PRIMARY_NAME.equals(name)) {
                throw new IllegalStateException("Reserved redis source name: " + KsetRedisRegistry.PRIMARY_NAME);
            }
            if (source == null || !source.isEnabled()) {
                continue;
            }
            LettuceConnectionFactory connectionFactory = KsetRedisConnectionFactoryBuilder.build(
                    name,
                    source,
                    connectionCustomizers.orderedStream().toList());
            connectionFactories.add(connectionFactory);
            RedisTemplate<String, Object> template =
                    KsetRedisTemplateFactory.create(connectionFactory, source.getKeyPrefix(), valueSerializer);
            KsetRedisService service = KsetRedisServiceAutoConfiguration.monitorEnabled(environment)
                    ? KsetRedisService.monitoredFrom(name, template, ttlPolicy, streamSettings)
                    : KsetRedisService.from(name, template, ttlPolicy, streamSettings);
            services.put(name, service);
            beanFactory.registerSingleton(namedServiceBeanName(name), service);
        }
        return new KsetRedisNamedSources(services, connectionFactories);
    }
}
