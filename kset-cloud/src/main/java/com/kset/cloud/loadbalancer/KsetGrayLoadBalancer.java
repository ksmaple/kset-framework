package com.kset.cloud.loadbalancer;

import com.kset.cloud.config.KsetCloudProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.EmptyResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 基于灰度标签的 LoadBalancer（nacos / gateway 共享）。
 */
public class KsetGrayLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;
    private final KsetCloudProperties properties;

    public KsetGrayLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplierProvider,
                                KsetCloudProperties properties) {
        this.supplierProvider = supplierProvider;
        this.properties = properties;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable();
        if (supplier == null) {
            return Mono.just(new EmptyResponse());
        }
        return supplier.get().next().map(instances -> chooseInstance(instances, request));
    }

    private Response<ServiceInstance> chooseInstance(List<ServiceInstance> instances, Request request) {
        if (instances == null || instances.isEmpty()) {
            return new EmptyResponse();
        }
        String grayTag = resolveGrayTag(request);
        String metadataKey = properties.getLoadbalancer().getMetadataKey();

        List<ServiceInstance> candidates = instances;
        if (grayTag != null && !grayTag.isBlank()) {
            List<ServiceInstance> matched = instances.stream()
                    .filter(instance -> grayTag.equals(instance.getMetadata().get(metadataKey)))
                    .collect(Collectors.toList());
            if (!matched.isEmpty()) {
                candidates = matched;
            }
        }

        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return new DefaultResponse(candidates.get(index));
    }

    private String resolveGrayTag(Request request) {
        if (!(request.getContext() instanceof RequestDataContext context)) {
            return null;
        }
        RequestData clientRequest = context.getClientRequest();
        if (clientRequest == null) {
            return null;
        }
        HttpHeaders headers = clientRequest.getHeaders();
        if (headers == null) {
            return null;
        }
        return headers.getFirst(properties.getLoadbalancer().getGrayHeader());
    }
}
