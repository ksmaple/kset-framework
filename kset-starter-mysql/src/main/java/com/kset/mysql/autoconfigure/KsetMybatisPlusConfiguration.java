package com.kset.mysql.autoconfigure;

import com.kset.mysql.interceptor.SlowSqlMonitorInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 约定由 {@code application-kset-mysql.yml} 提供默认配置。
 */
@AutoConfiguration
public class KsetMybatisPlusConfiguration {

    @Bean
    @ConditionalOnBean(SqlSessionFactory.class)
    @ConditionalOnMissingClass("com.kset.monitor.interceptor.MybatisMonitorInterceptor")
    @ConditionalOnProperty(prefix = "kset.mysql.slow-sql", name = "enabled", havingValue = "true", matchIfMissing = true)
    public SlowSqlMonitorInterceptor slowSqlMonitorInterceptor() {
        return new SlowSqlMonitorInterceptor();
    }

    @Bean
    @ConditionalOnBean({SqlSessionFactory.class, SlowSqlMonitorInterceptor.class})
    public Object slowSqlMonitorInterceptorRegistrar(SqlSessionFactory sqlSessionFactory,
                                                     SlowSqlMonitorInterceptor interceptor) {
        sqlSessionFactory.getConfiguration().addInterceptor(interceptor);
        return new Object();
    }
}
