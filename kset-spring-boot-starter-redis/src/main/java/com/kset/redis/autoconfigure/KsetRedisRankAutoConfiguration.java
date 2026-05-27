package com.kset.redis.autoconfigure;

import com.kset.redis.rank.KsetRedisRankService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
@ConditionalOnBean(RedisTemplate.class)
public class KsetRedisRankAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KsetRedisRankService ksetRedisRankService(RedisTemplate<String, Object> redisTemplate) {
        return KsetRedisRankService.builder(redisTemplate).build();
    }
}
