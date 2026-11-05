package com.kset.common.service;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class KsetServiceRefBeanPostProcessor implements BeanPostProcessor, BeanFactoryAware {

    private final List<LocalReference> localReferences = new CopyOnWriteArrayList<>();
    private ConfigurableListableBeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ConfigurableListableBeanFactory configurableBeanFactory)) {
            throw new IllegalStateException("@KsetReference requires a configurable Spring BeanFactory");
        }
        this.beanFactory = configurableBeanFactory;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithFields(ClassUtils.getUserClass(bean), field -> {
            KsetReference annotation = field.getAnnotation(KsetReference.class);
            if (annotation == null) {
                return;
            }
            validateField(field, beanName);
            Object service = resolveService(field, beanName, annotation);
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, bean, service);
        });
        return bean;
    }

    private void validateField(Field field, String beanName) {
        if (!field.getType().isInterface() || Modifier.isStatic(field.getModifiers())
                || Modifier.isFinal(field.getModifiers())) {
            throw new BeanCreationException(beanName,
                    "@KsetReference requires a non-static, non-final interface field: " + field);
        }
    }

    private Object resolveService(Field field, String beanName, KsetReference annotation) {
        ServiceReferenceKey key = new ServiceReferenceKey(
                field.getType(), resolveAttribute(annotation.group()), resolveAttribute(annotation.version()));
        if (beanFactory.getBeanNamesForType(field.getType(), true, false).length > 0) {
            Object local = beanFactory.resolveDependency(new DependencyDescriptor(field, true), beanName);
            if (local == null) {
                throw new BeanCreationException(beanName, "No matching local service for " + field);
            }
            localReferences.add(new LocalReference(beanName, field.getName(), key));
            return local;
        }
        RemoteServiceReferenceProvider provider = availableRemoteProvider();
        if (provider == null) {
            throw new BeanCreationException(beanName, "No local service for " + field
                    + " and no available remote service provider");
        }
        Object remote = provider.getReference(key);
        if (remote == null || !field.getType().isInstance(remote)) {
            throw new BeanCreationException(beanName, "Remote service provider returned an invalid reference for " + field);
        }
        return remote;
    }

    public void validateLocalReferences() {
        if (localReferences.isEmpty()) {
            return;
        }
        RemoteServiceReferenceProvider provider = availableRemoteProvider();
        if (provider == null) {
            return;
        }
        Map<ServiceReferenceKey, Boolean> checked = new HashMap<>();
        for (LocalReference local : localReferences) {
            ServiceReferenceKey key = local.key();
            boolean conflict;
            try {
                conflict = checked.computeIfAbsent(key, provider::hasOtherProcessProvider);
            } catch (RuntimeException error) {
                throw new IllegalStateException("Failed to check remote service providers for " + key, error);
            }
            if (conflict) {
                throw new IllegalStateException("@KsetReference conflict at " + local.beanName() + "."
                        + local.fieldName() + ": local Spring bean and another process both provide " + key);
            }
        }
    }

    private RemoteServiceReferenceProvider availableRemoteProvider() {
        RemoteServiceReferenceProvider provider = beanFactory.getBeanProvider(RemoteServiceReferenceProvider.class)
                .getIfAvailable();
        return provider != null && provider.isAvailable() ? provider : null;
    }

    private String resolveAttribute(String value) {
        String resolved = beanFactory.resolveEmbeddedValue(value);
        return resolved != null ? resolved.trim() : "";
    }

    private record LocalReference(String beanName, String fieldName, ServiceReferenceKey key) {
    }
}
