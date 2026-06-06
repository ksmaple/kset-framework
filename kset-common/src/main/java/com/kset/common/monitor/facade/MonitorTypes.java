package com.kset.common.monitor.facade;

/**
 * 标准 Transaction/Event type 常量（对齐 CAT 命名约定）。
 */
public final class MonitorTypes {

    public static final String URL = "URL";
    public static final String SQL = "SQL";
    public static final String RPC = "RPC";
    public static final String RPC_CONSUMER = "RPC.Consumer";
    public static final String RPC_PROVIDER = "RPC.Provider";
    public static final String CACHE = "Cache";
    public static final String MQ = "MQ";
    public static final String BIZ = "Biz";
    public static final String HTTP_CLIENT = "HttpClient";
    public static final String THREAD_POOL = "ThreadPool";
    public static final String SCHEDULED_TASK = "ScheduledTask";
    public static final String ERROR = "Error";

    private MonitorTypes() {
    }
}
