package com.kset.common.utils.thread;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
public class KsetThreadPoolFactory {

    private static final KsetThreadPoolFactory INSTANCE = new KsetThreadPoolFactory();

    private final ConcurrentHashMap<String, KsetThreadPoolExecutor> pools = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PoolConfig> configs = new ConcurrentHashMap<>();

    private volatile PoolConfig defaultConfig = PoolConfig.defaultConfig();
    private final AtomicBoolean globalShutdown = new AtomicBoolean(false);

    
    private volatile ThreadPoolReporter globalReporter;
    private volatile ThreadPoolTraceAdapter globalTraceContextAdapter;

    private KsetThreadPoolFactory() {
    }

    public static KsetThreadPoolFactory getInstance() {
        return INSTANCE;
    }

    // ========== 配置注册 ==========

    /**
     * 注册业务自定义配置（首次 execute 前调用，否则使用默认配置）。
     */
    public void register(String bizName, PoolConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null for biz: " + bizName);
        }
        configs.put(bizName, config);
        log.info(String.format("[Factory] Registered config for biz '%s': core=%d, max=%d, queue=%d",
                bizName, config.getCorePoolSize(), config.getMaximumPoolSize(), config.getQueueCapacity()));
    }

    /**
     * 设置全局默认配置（影响所有未 register 的业务）。
     */
    public void setDefaultConfig(PoolConfig config) {
        this.defaultConfig = config != null ? config : PoolConfig.defaultConfig();
        log.info(String.format("[Factory] Default config updated: core=%d, max=%d",
                defaultConfig.getCorePoolSize(), defaultConfig.getMaximumPoolSize()));
    }

    
    public void setGlobalReporter(ThreadPoolReporter reporter) {
        this.globalReporter = reporter;
        log.info(String.format("[Factory] Global reporter set: %b", reporter != null));
    }

    
    public void setGlobalTraceContextAdapter(ThreadPoolTraceAdapter adapter) {
        this.globalTraceContextAdapter = adapter;
        log.info(String.format("[Factory] Global trace adapter set: %b", adapter != null));
    }

    /**
     * 为指定业务设置上报器（覆盖全局设置）。
     */
    public void setReporter(String bizName, ThreadPoolReporter reporter) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        if (pool != null) {
            pool.setReporter(reporter);
        }
    }

    
    public void setTraceContextAdapter(String bizName, ThreadPoolTraceAdapter adapter) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        if (pool != null) {
            pool.setTraceContextAdapter(adapter);
        }
    }

    // ========== execute 系列 ==========

    
    public void execute(String bizName, Runnable task) {
        getOrCreatePool(bizName).execute(task);
    }

    /**
     * 执行带优先级的业务任务（仅当该业务启用了 priorityQueue 时有效）。
     */
    public void execute(String bizName, Runnable task, int priority) {
        getOrCreatePool(bizName).execute(task, priority);
    }

    // ========== submit 系列 ==========

    public <T> Future<T> submit(String bizName, Callable<T> task) {
        return getOrCreatePool(bizName).submit(task);
    }

    public Future<?> submit(String bizName, Runnable task) {
        return getOrCreatePool(bizName).submit(task);
    }

    public <T> Future<T> submit(String bizName, Callable<T> task, int priority) {
        return getOrCreatePool(bizName).submit(task, priority);
    }

    public Future<?> submit(String bizName, Runnable task, int priority) {
        return getOrCreatePool(bizName).submit(task, priority);
    }

    public <T> Future<T> submit(String bizName, Runnable task, T result, int priority) {
        return getOrCreatePool(bizName).submit(task, result, priority);
    }

    // ========== 动态调参 ==========

    public void setCorePoolSize(String bizName, int corePoolSize) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        if (pool != null) {
            pool.setCorePoolSize(corePoolSize);
        }
    }

    public void setMaximumPoolSize(String bizName, int maximumPoolSize) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        if (pool != null) {
            pool.setMaximumPoolSize(maximumPoolSize);
        }
    }

    public void setTargetLatencyMs(String bizName, long targetLatencyMs) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        if (pool != null) {
            pool.setTargetLatencyMs(targetLatencyMs);
        }
    }

    public void setAutoTuneEnabled(String bizName, boolean enabled) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        if (pool != null) {
            pool.setAutoTuneEnabled(enabled);
        }
    }

    public void setKeepAliveTimeMs(String bizName, long keepAliveTimeMs) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        if (pool != null) {
            pool.setKeepAliveTimeMs(keepAliveTimeMs);
        }
    }

    public void setDefaultPriority(String bizName, int priority) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        if (pool != null) {
            pool.setDefaultPriority(priority);
        }
    }

    // ========== 指标查询 ==========

    
    public ThreadPoolMetrics getMetrics(String bizName) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        return pool != null ? pool.getMetrics() : null;
    }

    /**
     * 手动触发指定业务的指标全量上报。
     */
    public void reportMetrics(String bizName) {
        KsetThreadPoolExecutor pool = getPool(bizName);
        if (pool != null) {
            pool.reportMetrics();
        }
    }

    
    public void reportAllMetrics() {
        pools.forEach((k, v) -> v.reportMetrics());
    }

    
    public Map<String, ThreadPoolMetrics> getAllMetrics() {
        Map<String, ThreadPoolMetrics> result = new HashMap<>();
        pools.forEach((k, v) -> result.put(k, v.getMetrics()));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 获取已注册的业务名称列表。
     */
    public Map<String, PoolConfig> getAllConfigs() {
        return Collections.unmodifiableMap(new HashMap<>(configs));
    }

    // ========== 生命周期 ==========

    public void shutdown(String bizName) {
        KsetThreadPoolExecutor pool = pools.remove(bizName);
        if (pool != null) {
            pool.shutdown();
            log.info(String.format("[Factory] Shutdown pool for biz '%s'", bizName));
        }
        configs.remove(bizName);
    }

    public void shutdownAll() {
        if (globalShutdown.compareAndSet(false, true)) {
            pools.forEach((k, v) -> {
                v.shutdown();
                log.info(String.format("[Factory] Shutdown pool for biz '%s'", k));
            });
            pools.clear();
            configs.clear();
        }
    }

    // ========== 内部 ==========

    private KsetThreadPoolExecutor getOrCreatePool(String bizName) {
        if (globalShutdown.get()) {
            throw new RejectedExecutionException("Factory is shutdown");
        }
        return pools.computeIfAbsent(bizName, this::createPool);
    }

    private KsetThreadPoolExecutor createPool(String bizName) {
        PoolConfig config = configs.get(bizName);
        if (config == null) {
            config = defaultConfig;
            log.info(String.format("[Factory] Using default config for biz '%s'", bizName));
        }

        KsetThreadPoolExecutor.Builder builder = KsetThreadPoolExecutor.newBuilder(bizName)
                .corePoolSize(config.getCorePoolSize())
                .maximumPoolSize(config.getMaximumPoolSize())
                .keepAliveTimeMs(config.getKeepAliveTimeMs())
                .queueCapacity(config.getQueueCapacity())
                .targetLatencyMs(config.getTargetLatencyMs())
                .autoTuneEnabled(config.isAutoTuneEnabled())
                .tuneIntervalMs(config.getTuneIntervalMs())
                .priorityQueue(config.isPriorityQueue())
                .defaultPriority(config.getDefaultPriority());

        if (config.getRejectedHandler() != null) {
            builder.rejectedExecutionHandler(config.getRejectedHandler());
        }
        if (config.getReporter() != null) {
            builder.reporter(config.getReporter());
        } else if (globalReporter != null) {
            builder.reporter(globalReporter);
        }
        if (config.getTraceContextAdapter() != null) {
            builder.traceContextAdapter(config.getTraceContextAdapter());
        } else if (globalTraceContextAdapter != null) {
            builder.traceContextAdapter(globalTraceContextAdapter);
        }

        KsetThreadPoolExecutor pool = builder.build();
        log.info(String.format("[Factory] Created pool for biz '%s': core=%d, max=%d, queue=%d, autoTune=%b, priorityQueue=%b, reporter=%b, traceAdapter=%b",
                bizName, config.getCorePoolSize(), config.getMaximumPoolSize(),
                config.getQueueCapacity(), config.isAutoTuneEnabled(), config.isPriorityQueue(),
                pool.getReporter() != null, pool.getTraceContextAdapter() != null));
        return pool;
    }

    private KsetThreadPoolExecutor getPool(String bizName) {
        return pools.get(bizName);
    }

    // ========== 配置类 ==========

    @Data
    @Builder
    public static class PoolConfig {
        @Builder.Default
        private int corePoolSize = 32;
        @Builder.Default
        private int maximumPoolSize = 200;
        @Builder.Default
        private long keepAliveTimeMs = 4000L;
        @Builder.Default
        private int queueCapacity = 100;
        @Builder.Default
        private long targetLatencyMs = 1000L;
        @Builder.Default
        private boolean autoTuneEnabled = false;
        @Builder.Default
        private long tuneIntervalMs = 10000L;
        @Builder.Default
        private boolean priorityQueue = false;
        @Builder.Default
        private int defaultPriority = 5;
        private RejectedExecutionHandler rejectedHandler;
        private ThreadPoolReporter reporter;
        private ThreadPoolTraceAdapter traceContextAdapter;

        public static PoolConfig defaultConfig() {
            return PoolConfig.builder().build();
        }

        public static PoolConfig ioConfig() {
            int cpu = Runtime.getRuntime().availableProcessors();
            return PoolConfig.builder()
                    .corePoolSize(cpu * 2)
                    .maximumPoolSize(Math.max(cpu * 4, 32))
                    .queueCapacity(500)
                    .targetLatencyMs(100)
                    .autoTuneEnabled(true)
                    .build();
        }

        public static PoolConfig cpuConfig() {
            int cpu = Runtime.getRuntime().availableProcessors();
            return PoolConfig.builder()
                    .corePoolSize(cpu)
                    .maximumPoolSize(cpu + 1)
                    .queueCapacity(50)
                    .targetLatencyMs(50)
                    .autoTuneEnabled(true)
                    .build();
        }

        public static PoolConfig lowLatencyConfig() {
            int cpu = Runtime.getRuntime().availableProcessors();
            return PoolConfig.builder()
                    .corePoolSize(cpu)
                    .maximumPoolSize(Math.max(cpu * 8, 16))
                    .queueCapacity(10)
                    .targetLatencyMs(10)
                    .autoTuneEnabled(true)
                    .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())
                    .priorityQueue(true)
                    .defaultPriority(5)
                    .build();
        }

        public static PoolConfig highThroughputConfig() {
            int cpu = Runtime.getRuntime().availableProcessors();
            return PoolConfig.builder()
                    .corePoolSize(cpu * 2)
                    .maximumPoolSize(Math.max(cpu * 4, 32))
                    .queueCapacity(2000)
                    .targetLatencyMs(500)
                    .autoTuneEnabled(true)
                    .build();
        }

        public static PoolConfig mixedConfig() {
            int cpu = Runtime.getRuntime().availableProcessors();
            return PoolConfig.builder()
                    .corePoolSize(cpu)
                    .maximumPoolSize(Math.max(cpu * 2, 16))
                    .queueCapacity(200)
                    .targetLatencyMs(200)
                    .autoTuneEnabled(true)
                    .build();
        }
    }
}
