package com.kset.auth.autoconfigure;

import com.kset.auth.config.KsetAuthProperties;
import com.kset.auth.core.AuthRuleResolver;
import com.kset.auth.core.AppTokenAuthenticator;
import com.kset.auth.core.DefaultLoginUserHeaderCodec;
import com.kset.auth.core.LoginAuthService;
import com.kset.auth.core.NoneAuthenticator;
import com.kset.auth.core.SessionAuthenticator;
import com.kset.auth.core.SignatureAuthenticator;
import com.kset.auth.core.TrustedHeaderAuthenticator;
import com.kset.auth.session.LoginSessionStore;
import com.kset.auth.spi.Authenticator;
import com.kset.auth.spi.LoginUserHeaderCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration(afterName = {
        "com.kset.redis.autoconfigure.KsetRedisAutoConfiguration"
})
@ConditionalOnProperty(prefix = "kset.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetAuthProperties.class)
public class KsetAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LoginUserHeaderCodec loginUserHeaderCodec() {
        return new DefaultLoginUserHeaderCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthRuleResolver authRuleResolver(KsetAuthProperties properties) {
        return new AuthRuleResolver(properties);
    }

    @Bean
    @ConditionalOnBean(LoginSessionStore.class)
    @ConditionalOnMissingBean(SessionAuthenticator.class)
    public Authenticator sessionAuthenticator(LoginSessionStore sessionStore) {
        return new SessionAuthenticator(sessionStore);
    }

    @Bean
    @ConditionalOnMissingBean(TrustedHeaderAuthenticator.class)
    public Authenticator trustedHeaderAuthenticator(LoginUserHeaderCodec headerCodec) {
        return new TrustedHeaderAuthenticator(headerCodec);
    }

    @Bean
    @ConditionalOnMissingBean(SignatureAuthenticator.class)
    public Authenticator signatureAuthenticator(KsetAuthProperties properties) {
        return new SignatureAuthenticator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(AppTokenAuthenticator.class)
    public Authenticator appTokenAuthenticator(KsetAuthProperties properties) {
        return new AppTokenAuthenticator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(NoneAuthenticator.class)
    public Authenticator noneAuthenticator() {
        return new NoneAuthenticator();
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginAuthService loginAuthService(AuthRuleResolver ruleResolver,
                                             List<Authenticator> authenticators,
                                             org.springframework.beans.factory.ObjectProvider<LoginSessionStore> sessionStore) {
        return new LoginAuthService(sessionStore.getIfAvailable(), ruleResolver, authenticators);
    }
}
