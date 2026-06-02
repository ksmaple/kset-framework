package com.kset.auth.autoconfigure;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.dubbo.LoginContextDubboFilter;
import com.kset.auth.spi.LoginUserHeaderCodec;
import org.apache.dubbo.rpc.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = KsetAuthAutoConfiguration.class)
@ConditionalOnClass(Filter.class)
@ConditionalOnProperty(prefix = "kset.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KsetAuthDubboAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "loginContextDubboFilter")
    @ConditionalOnProperty(prefix = "kset.auth.dubbo", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Filter loginContextDubboFilter(KsetAuthProperties properties, LoginUserHeaderCodec headerCodec) {
        return new LoginContextDubboFilter(properties, headerCodec);
    }
}
