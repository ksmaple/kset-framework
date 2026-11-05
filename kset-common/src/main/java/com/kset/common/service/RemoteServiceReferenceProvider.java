package com.kset.common.service;

/** Optional adapter supplied by an RPC starter. */
public interface RemoteServiceReferenceProvider {

    boolean isAvailable();

    Object getReference(ServiceReferenceKey key);

    boolean hasOtherProcessProvider(ServiceReferenceKey key);
}
