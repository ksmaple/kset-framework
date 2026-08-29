package com.kset.cache.core;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class KsetCacheBootstrap implements InitializingBean, DisposableBean {

    private final KsetCacheFacade cacheFacade;

    public KsetCacheBootstrap(KsetCacheFacade cacheFacade) {
        this.cacheFacade = cacheFacade;
    }

    @Override
    public void afterPropertiesSet() {
        KsetCache.bind(cacheFacade);
    }

    @Override
    public void destroy() {
        KsetCache.unbind();
    }
}
