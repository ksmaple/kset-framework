package com.kset.dubbo.reference;

import com.kset.common.service.ServiceReferenceKey;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.client.ServiceDiscoveryRegistry;
import org.apache.dubbo.registry.support.RegistryManager;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.directory.AbstractDirectory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class DubboRemoteServiceInspector {

    private static final long DISCOVERY_WAIT_NANOS = TimeUnit.SECONDS.toNanos(2);

    boolean hasOtherProcessProvider(ServiceReferenceKey key) {
        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        ReferenceConfig<Object> reference = new ReferenceConfig<>();
        reference.setInterface(key.serviceType());
        reference.setScope("remote");
        if (!key.group().isBlank()) {
            reference.setGroup(key.group());
        }
        if (!key.version().isBlank()) {
            reference.setVersion(key.version());
        }
        try {
            bootstrap.reference(reference);
            reference.get(false);
            List<URL> local = localProviderUrls(bootstrap);
            long deadline = System.nanoTime() + DISCOVERY_WAIT_NANOS;
            do {
                if (hasOtherProcessProvider(providerUrls(reference), local, reference.getUniqueServiceName())) {
                    return true;
                }
                if (System.nanoTime() >= deadline) {
                    ensureRegistryReachable(bootstrap);
                    return false;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while checking Dubbo providers", error);
                }
            } while (true);
        } finally {
            reference.destroy();
        }
    }

    private void ensureRegistryReachable(DubboBootstrap bootstrap) {
        List<Registry> registries = List.copyOf(
                RegistryManager.getInstance(bootstrap.getApplicationModel()).getRegistries());
        if (registries.isEmpty()) {
            throw new IllegalStateException("No Dubbo registry is available for conflict validation");
        }
        for (Registry registry : registries) {
            if (registry instanceof ServiceDiscoveryRegistry discoveryRegistry) {
                discoveryRegistry.getServiceDiscovery().getServices();
            } else if (!registry.isAvailable()) {
                throw new IllegalStateException("Dubbo registry is unavailable: " + registry.getUrl());
            }
        }
    }

    static boolean hasOtherProcessProvider(List<URL> discovered, List<URL> local, String serviceKey) {
        Set<ProviderAddress> localAddresses = new HashSet<>();
        local.stream().map(ProviderAddress::from).forEach(localAddresses::add);
        return discovered.stream()
                .filter(url -> serviceKey.equals(url.getServiceKey()))
                .map(ProviderAddress::from)
                .anyMatch(address -> !localAddresses.contains(address));
    }

    private List<URL> localProviderUrls(DubboBootstrap bootstrap) {
        return bootstrap.getApplicationModel().getModuleModels().stream()
                .flatMap(module -> module.getServiceRepository().getExportedServices().stream())
                .flatMap(provider -> provider.getStatedUrl().stream())
                .map(statedUrl -> statedUrl.getProviderUrl())
                .filter(url -> url != null)
                .toList();
    }

    @SuppressWarnings("deprecation")
    private List<URL> providerUrls(ReferenceConfig<?> reference) {
        // Dubbo 3.3.6 exposes the subscribed provider directory only through this invoker.
        Invoker<?> invoker = reference.getInvoker();
        if (invoker == null) {
            throw new IllegalStateException("Dubbo reference has no invoker");
        }
        List<URL> urls = new ArrayList<>();
        collectProviderUrls(invoker, urls, new HashSet<>());
        return urls;
    }

    private void collectProviderUrls(Invoker<?> invoker, List<URL> urls, Set<Invoker<?>> visited) {
        if (!visited.add(invoker)) {
            return;
        }
        if (invoker instanceof ClusterInvoker<?> cluster) {
            Directory<?> directory = cluster.getDirectory();
            if (!(directory instanceof AbstractDirectory<?> knownDirectory)) {
                throw new IllegalStateException("Unsupported Dubbo provider directory: " + directory);
            }
            for (Invoker<?> provider : knownDirectory.getInvokers()) {
                collectProviderUrls(provider, urls, visited);
            }
            return;
        }
        URL url = invoker.getUrl();
        if (url == null || url.getPort() <= 0 || url.getServiceKey() == null) {
            throw new IllegalStateException("Dubbo provider address is unavailable: " + invoker);
        }
        urls.add(url);
    }

    private record ProviderAddress(String protocol, String host, int port, String serviceKey) {
        static ProviderAddress from(URL url) {
            return new ProviderAddress(url.getProtocol(), url.getHost(), url.getPort(), url.getServiceKey());
        }
    }
}
