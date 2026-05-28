package com.kset.monitor.facade;

/**
 * 监控事务抽象（对齐 CAT Transaction，须 complete/close）。
 */
public interface MonitorTransaction extends AutoCloseable {

    void setStatus(MonitorStatus status);

    void setStatus(Throwable throwable);

    void addData(String key, String value);

    String getType();

    String getName();

    @Override
    void close();
}
