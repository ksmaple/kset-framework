package com.kset.common.service;

public record ServiceReferenceKey(Class<?> serviceType, String group, String version) {

    @Override
    public String toString() {
        return serviceType.getName() + " (group=" + group + ", version=" + version + ")";
    }
}
