package com.kset.dubbo.reference;

import com.kset.common.service.RemoteServiceReferenceProvider;
import com.kset.common.service.ServiceReferenceKey;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DubboServiceReferenceProvider implements RemoteServiceReferenceProvider, DisposableBean {

    private final Map<ServiceReferenceKey, ReferenceHolder> remoteReferences = new ConcurrentHashMap<>();
    private final DubboRemoteServiceInspector inspector = new DubboRemoteServiceInspector();
    private final boolean available;

    public DubboServiceReferenceProvider(Environment environment) {
        String address = environment.getProperty("dubbo.registry.address");
        this.available = address != null && !address.isBlank() && !"N/A".equalsIgnoreCase(address.trim());
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Object getReference(ServiceReferenceKey key) {
        return remoteReferences.computeIfAbsent(key, referenceKey -> {
            ReferenceConfig<Object> reference = new ReferenceConfig<>();
            reference.setInterface(referenceKey.serviceType());
            reference.setScope("remote");
            if (!referenceKey.group().isBlank()) {
                reference.setGroup(referenceKey.group());
            }
            if (!referenceKey.version().isBlank()) {
                reference.setVersion(referenceKey.version());
            }
            try {
                DubboBootstrap.getInstance().reference(reference);
                Object service = reference.get();
                if (service == null) {
                    throw new IllegalStateException("Dubbo reference returned no service for "
                            + referenceKey.serviceType().getName());
                }
                return new ReferenceHolder(reference, service);
            } catch (RuntimeException error) {
                reference.destroy();
                throw error;
            }
        }).service();
    }

    @Override
    public boolean hasOtherProcessProvider(ServiceReferenceKey key) {
        return inspector.hasOtherProcessProvider(key);
    }

    @Override
    public void destroy() {
        remoteReferences.values().forEach(holder -> holder.reference().destroy());
        remoteReferences.clear();
    }

    private record ReferenceHolder(ReferenceConfig<?> reference, Object service) {
    }
}
