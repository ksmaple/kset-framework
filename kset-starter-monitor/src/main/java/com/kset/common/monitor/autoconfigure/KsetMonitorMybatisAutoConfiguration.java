package com.kset.common.monitor.autoconfigure;

import com.kset.common.monitor.interceptor.MybatisMonitorInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis SQL 监控自动装配。
 *
 * <p>独立于具体数据库驱动，MySQL、PostgreSQL、SQLite 组件只要引入 datasource 能力即可复用。</p>
 */
@AutoConfiguration(beforeName = {
        "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
        "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"
})
@ConditionalOnClass(Interceptor.class)
@ConditionalOnProperty(prefix = "kset.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetMonitorMybatisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "kset.monitor.mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
    public MybatisMonitorInterceptor mybatisMonitorInterceptor() {
        return new MybatisMonitorInterceptor();
    }
}
