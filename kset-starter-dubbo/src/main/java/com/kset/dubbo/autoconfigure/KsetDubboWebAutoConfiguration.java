package com.kset.dubbo.autoconfigure;

import com.kset.dubbo.web.DubboRpcExceptionHandler;
import com.kset.web.config.KsetWebProperties;
import com.kset.web.response.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(ApiResponse.class)
public class KsetDubboWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DubboRpcExceptionHandler dubboRpcExceptionHandler(ObjectProvider<KsetWebProperties> webProperties) {
        return new DubboRpcExceptionHandler(webProperties.getIfAvailable(KsetWebProperties::new));
    }
}
