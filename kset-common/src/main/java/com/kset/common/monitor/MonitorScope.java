package com.kset.common.monitor;

/**
 * try-with-resources 链路作用域，退出时恢复或清理上下文。
 */
public final class MonitorScope implements AutoCloseable {

    private final TraceSnapshot previous;

    public MonitorScope(TraceSnapshot previous) {
        this.previous = previous;
    }

    @Override
    public void close() {
        if (previous != null) {
            com.kset.monitor.Monitor.restore(previous);
        } else {
            com.kset.monitor.Monitor.clear();
        }
    }
}
