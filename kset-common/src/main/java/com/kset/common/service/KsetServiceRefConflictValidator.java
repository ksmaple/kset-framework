package com.kset.common.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

public class KsetServiceRefConflictValidator implements ApplicationRunner, Ordered {

    private final KsetServiceRefBeanPostProcessor processor;

    public KsetServiceRefConflictValidator(KsetServiceRefBeanPostProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) {
        processor.validateLocalReferences();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
