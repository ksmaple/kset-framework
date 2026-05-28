package com.kset.monitor.internal;

import com.kset.monitor.facade.MonitorStatus;
import com.kset.monitor.facade.MonitorTransaction;

/**
 * 无操作事务（未装配 starter 时使用）。
 */
public final class NoOpMonitorTransaction implements MonitorTransaction {

    private final String type;
    private final String name;

    public NoOpMonitorTransaction(String type, String name) {
        this.type = type;
        this.name = name;
    }

    @Override
    public void setStatus(MonitorStatus status) {
    }

    @Override
    public void setStatus(Throwable throwable) {
    }

    @Override
    public void addData(String key, String value) {
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void close() {
    }
}
