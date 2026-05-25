package com.kset.cloud.gateway.autoconfigure;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kset.cloud.config.KsetCloudProperties;
import com.kset.cloud.gateway.auth.HeaderTokenGatewayAuthProvider;
import com.kset.cloud.gateway.auth.PassThroughGatewayAuthProvider;
import com.kset.cloud.gateway.filter.AuthGatewayFilter;
import com.kset.cloud.gateway.spi.GatewayAuthProvider;
import com.kset.cloud.gateway.filter.GrayTagGatewayFilter;
import com.kset.cloud.gateway.filter.TraceIdGatewayFilter;
import com.kset.cloud.gateway.route.GatewayRouteRuleProvider;
import com.kset.cloud.nacos.NacosConfigConvention;
import com.kset.cloud.loadbalancer.KsetGrayLoadBalancerConfiguration;
import com.kset.cloud.spi.GrayTagResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.cloud.gateway.filter.GatewayFilterChain")
@ConditionalOnProperty(prefix = "kset.cloud.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KsetCloudProperties.class)
@LoadBalancerClients(defaultConfiguration = KsetGrayLoadBalancerConfiguration.class)
@Import(KsetGrayLoadBalancerConfiguration.class)
public class KsetGatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KsetGatewayAutoConfiguration.class);

    @Bean
    public GlobalFilter traceIdGatewayFilter(KsetCloudProperties properties) {
        return new TraceIdGatewayFilter(properties);
    }

    @Bean
    public GlobalFilter grayTagGatewayFilter(KsetCloudProperties properties, GrayTagResolver grayTagResolver) {
        return new GrayTagGatewayFilter(properties, grayTagResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayAuthProvider passThroughGatewayAuthProvider() {
        return new PassThroughGatewayAuthProvider();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.cloud.gateway", name = "auth-enabled", havingValue = "true")
    @ConditionalOnMissingBean(HeaderTokenGatewayAuthProvider.class)
    public GatewayAuthProvider headerTokenGatewayAuthProvider(KsetCloudProperties properties) {
        return new HeaderTokenGatewayAuthProvider(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.cloud.gateway", name = "auth-enabled", havingValue = "true")
    public GlobalFilter authGatewayFilter(org.springframework.beans.factory.ObjectProvider<GatewayAuthProvider> providers) {
        return new AuthGatewayFilter(providers);
    }

    @Bean
    public GatewayRouteRuleProvider gatewayRouteRuleProvider(RouteDefinitionWriter routeDefinitionWriter,
                                                             ApplicationEventPublisher eventPublisher) {
        return new GatewayRouteRuleProvider(routeDefinitionWriter, eventPublisher);
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.cloud.gateway", name = "cors-enabled", havingValue = "true", matchIfMissing = true)
    public CorsWebFilter ksetGatewayCorsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern(CorsConfiguration.ALL);
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.addAllowedMethod(CorsConfiguration.ALL);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    @Order(-1)
    public ErrorWebExceptionHandler ksetGatewayErrorWebExceptionHandler() {
        return (ServerWebExchange exchange, Throwable ex) -> {
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"code\":-1,\"message\":\"Gateway error\",\"data\":null}";
            DataBuffer buffer = exchange.getResponse().bufferFactory()
                    .wrap(body.getBytes(StandardCharsets.UTF_8));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "kset.cloud.gateway", name = "sentinel-enabled", havingValue = "true", matchIfMissing = true)
    public GatewaySentinelRuleLoader gatewaySentinelRuleLoader(KsetCloudProperties properties,
                                                               NacosConfigConvention convention,
                                                               Environment environment) {
        return new GatewaySentinelRuleLoader(properties, convention, environment);
    }

    static class GatewaySentinelRuleLoader {

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        GatewaySentinelRuleLoader(KsetCloudProperties properties,
                                  NacosConfigConvention convention,
                                  Environment environment) {
            String serverAddr = environment.getProperty("spring.cloud.nacos.config.server-addr");
            if (serverAddr == null || serverAddr.isBlank()) {
                serverAddr = environment.getProperty("spring.cloud.nacos.discovery.server-addr");
            }
            if (serverAddr == null || serverAddr.isBlank()) {
                log.warn("Nacos server address not configured, skip Gateway Sentinel rule loading");
                return;
            }

            String appName = environment.getProperty("spring.application.name", "application");
            String dataId = properties.getSentinel().getGatewayFlowRuleDataId();
            if (dataId == null || dataId.isBlank()) {
                dataId = convention.gatewayFlowRuleDataId(appName);
            }

            ReadableDataSource<String, Set<GatewayFlowRule>> source = new NacosDataSource<>(
                    serverAddr, convention.group(), dataId,
                    json -> {
                        try {
                            List<GatewayFlowRule> rules = OBJECT_MAPPER.readValue(json,
                                    new TypeReference<List<GatewayFlowRule>>() {});
                            return new HashSet<>(rules);
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to parse gateway flow rules", e);
                        }
                    });
            GatewayRuleManager.register2Property(source.getProperty());
            log.info("Gateway Sentinel flow rules loaded from Nacos dataId={}", dataId);

            String degradeDataId = properties.getSentinel().getDegradeRuleDataId();
            if (degradeDataId == null || degradeDataId.isBlank()) {
                degradeDataId = appName + "-gateway-degrade-rules";
            }
            ReadableDataSource<String, List<DegradeRule>> degradeSource = new NacosDataSource<>(
                    serverAddr, convention.group(), degradeDataId,
                    json -> {
                        try {
                            return OBJECT_MAPPER.readValue(json, new TypeReference<List<DegradeRule>>() {});
                        } catch (Exception e) {
                            throw new IllegalStateException("Failed to parse gateway degrade rules", e);
                        }
                    });
            DegradeRuleManager.register2Property(degradeSource.getProperty());
            log.info("Gateway Sentinel degrade rules loaded from Nacos dataId={}", degradeDataId);
        }
    }
}
