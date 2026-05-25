package com.kset.cloud.loadbalancer;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer")
@LoadBalancerClients(defaultConfiguration = KsetGrayLoadBalancerConfiguration.class)
@Import(KsetGrayLoadBalancerConfiguration.class)
public class KsetGrayLoadBalancerAutoConfiguration {
}
