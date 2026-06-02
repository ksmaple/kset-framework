package com.kset.common.utils.thread;


import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kset.common.context.KsetContext;
import com.kset.common.context.KsetContextScope;
import com.kset.common.context.KsetContextSnapshot;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


@Slf4j
public class KsetThreadPoolExecutor extends ThreadPoolExecutor {

    // ========== 单例兼容（默认全局实例） ==========
    private static volatile KsetThreadPoolExecutor DEFAULT_INSTANCE;
    private static final Object DEFAULT_LOCK = new Object();

    // ========== 默认参数 ==========
    public static final int DEFAULT_CORE_POOL_SIZE = 32;
    public static final int DEFAULT_MAX_POOL_SIZE = 200;
    public static final long DEFAULT_KEEP_ALIVE_MS = 4000L;
    public static final int DEFAULT_QUEUE_CAPACITY = 100;
    public static final long DEFAULT_TARGET_LATENCY_MS = 1000L;
    public static final long DEFAULT_TUNE_INTERVAL_MS = 10000L;
    public static final int DEFAULT_WINDOW_SIZE = 10000;
    public static final int DEFAULT_PRIORITY = 5;

    // ========== 控制论调节参数 ==========
    /** 延迟死区比例：|误差| 小于此比例时不触发调节，避免系统振荡 */
    private static final double DEAD_ZONE_RATIO = 0.25;
    
    private static final int MAX_SINGLE_DELTA = 10;
    
    private static final int MIN_SINGLE_DELTA = 1;
    /** 缩容安全条件：队列使用率必须低于此值 */
    private static final double SHRINK_QUEUE_THRESHOLD = 0.3;
    
    private static final double REJECTION_RATE_THRESHOLD = 0.01;
    
    private static final double MAX_POOL_EXPAND_RATIO = 1.3;

    // ========== 实例属性 ==========
    private final String poolName;
    private volatile long targetLatencyMs;
    private volatile boolean autoTuneEnabled;
    private volatile long tuneIntervalMs;
    private final boolean priorityQueueEnabled;
    private volatile int defaultPriority;
    private final int initialMaximumPoolSize;
    private volatile long lastTuneSubmitted = 0;
    private volatile long lastTuneRejected = 0;

    // ========== 指标计数器 ==========
    private final AtomicLong submittedCounter = new AtomicLong(0);
    private final AtomicLong completedCounter = new AtomicLong(0);
    private final AtomicLong failedCounter = new AtomicLong(0);
    private final AtomicLong rejectedCounter = new AtomicLong(0);

    // ========== 执行记录滑动窗口 ==========
    private final int windowSize;
    private final ExecutionRecord[] windowBuffer;
    private final AtomicLong windowWritePos = new AtomicLong(0);

    // ========== 吞吐量差分统计 ==========
    private final AtomicLong lastSnapshotTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong lastCompletedSnapshot = new AtomicLong(0);
    private volatile double cachedThroughput = 0.0;

    // ========== 自动调节器 ==========
    private volatile ScheduledExecutorService tuner;
    private volatile long lastTuneTimeMs = 0;
    private final AtomicReference<String> lastTuneAction = new AtomicReference<>("none");

    // ========== 上报器 ==========
    private volatile ThreadPoolReporter reporter;
    private volatile java.util.function.Supplier<String> traceIdSupplier;

    
    private volatile ThreadPoolTraceAdapter traceContextAdapter;

    // ========== 优先级序列号生成器 ==========
    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    // ========== ThreadLocal 时间戳传递 ==========
    private final ThreadLocal<Long> startTimeHolder = new ThreadLocal<>();
    private final ThreadLocal<Long> waitTimeHolder = new ThreadLocal<>();
    private final ThreadLocal<FutureTask<?>> currentFutureTask = new ThreadLocal<>();
    private final ThreadLocal<KsetContextScope> currentContextScope = new ThreadLocal<>();

    // ========== 内部结构 ==========

    private static class TimedRunnable implements Runnable {
        final Runnable delegate;
        final long submitTime;
        final String traceId;
        final KsetContextSnapshot contextSnapshot;

        TimedRunnable(Runnable delegate, long submitTime, String traceId) {
            this(delegate, submitTime, traceId, KsetContext.capture());
        }

        TimedRunnable(Runnable delegate, long submitTime, String traceId, KsetContextSnapshot contextSnapshot) {
            this.delegate = delegate;
            this.submitTime = submitTime;
            this.traceId = traceId;
            this.contextSnapshot = contextSnapshot;
        }

        @Override
        public void run() {
            delegate.run();
        }
    }

    private static class TimedFutureTask<T> extends FutureTask<T> {
        final long submitTime;
        final String traceId;
        final KsetContextSnapshot contextSnapshot;

        TimedFutureTask(Callable<T> callable, long submitTime, String traceId) {
            this(callable, submitTime, traceId, KsetContext.capture());
        }

        TimedFutureTask(Callable<T> callable, long submitTime, String traceId, KsetContextSnapshot contextSnapshot) {
            super(callable);
            this.submitTime = submitTime;
            this.traceId = traceId;
            this.contextSnapshot = contextSnapshot;
        }

        TimedFutureTask(Runnable runnable, T result, long submitTime, String traceId) {
            this(runnable, result, submitTime, traceId, KsetContext.capture());
        }

        TimedFutureTask(Runnable runnable, T result, long submitTime, String traceId, KsetContextSnapshot contextSnapshot) {
            super(runnable, result);
            this.submitTime = submitTime;
            this.traceId = traceId;
            this.contextSnapshot = contextSnapshot;
        }
    }

    private static class PriorityRunnable implements Runnable, Comparable<PriorityRunnable> {
        final Runnable delegate;
        final long submitTime;
        final int priority;
        final long sequence;
        final String traceId;
        final KsetContextSnapshot contextSnapshot;

        PriorityRunnable(Runnable delegate, long submitTime, int priority, long sequence, String traceId) {
            this(delegate, submitTime, priority, sequence, traceId, KsetContext.capture());
        }

        PriorityRunnable(Runnable delegate, long submitTime, int priority, long sequence, String traceId, KsetContextSnapshot contextSnapshot) {
            this.delegate = delegate;
            this.submitTime = submitTime;
            this.priority = priority;
            this.sequence = sequence;
            this.traceId = traceId;
            this.contextSnapshot = contextSnapshot;
        }

        @Override
        public void run() {
            delegate.run();
        }

        @Override
        public int compareTo(PriorityRunnable other) {
            if (this.priority != other.priority) {
                return Integer.compare(other.priority, this.priority);
            }
            return Long.compare(this.sequence, other.sequence);
        }
    }

    private static class PriorityFutureTask<T> extends FutureTask<T> implements Comparable<PriorityFutureTask<?>> {
        final long submitTime;
        final int priority;
        final long sequence;
        final String traceId;
        final KsetContextSnapshot contextSnapshot;

        PriorityFutureTask(Callable<T> callable, long submitTime, int priority, long sequence, String traceId) {
            this(callable, submitTime, priority, sequence, traceId, KsetContext.capture());
        }

        PriorityFutureTask(Callable<T> callable, long submitTime, int priority, long sequence, String traceId, KsetContextSnapshot contextSnapshot) {
            super(callable);
            this.submitTime = submitTime;
            this.priority = priority;
            this.sequence = sequence;
            this.traceId = traceId;
            this.contextSnapshot = contextSnapshot;
        }

        PriorityFutureTask(Runnable runnable, T result, long submitTime, int priority, long sequence, String traceId) {
            this(runnable, result, submitTime, priority, sequence, traceId, KsetContext.capture());
        }

        PriorityFutureTask(Runnable runnable, T result, long submitTime, int priority, long sequence, String traceId, KsetContextSnapshot contextSnapshot) {
            super(runnable, result);
            this.submitTime = submitTime;
            this.priority = priority;
            this.sequence = sequence;
            this.traceId = traceId;
            this.contextSnapshot = contextSnapshot;
        }

        @Override
        public int compareTo(PriorityFutureTask<?> other) {
            if (this.priority != other.priority) {
                return Integer.compare(other.priority, this.priority);
            }
            return Long.compare(this.sequence, other.sequence);
        }
    }

    private static String getTraceId(Runnable r) {
        if (r instanceof PriorityRunnable) return ((PriorityRunnable) r).traceId;
        if (r instanceof PriorityFutureTask) return ((PriorityFutureTask<?>) r).traceId;
        if (r instanceof TimedRunnable) return ((TimedRunnable) r).traceId;
        if (r instanceof TimedFutureTask) return ((TimedFutureTask<?>) r).traceId;
        return null;
    }

    private static KsetContextSnapshot getContextSnapshot(Runnable r) {
        if (r instanceof PriorityRunnable) return ((PriorityRunnable) r).contextSnapshot;
        if (r instanceof PriorityFutureTask) return ((PriorityFutureTask<?>) r).contextSnapshot;
        if (r instanceof TimedRunnable) return ((TimedRunnable) r).contextSnapshot;
        if (r instanceof TimedFutureTask) return ((TimedFutureTask<?>) r).contextSnapshot;
        return null;
    }

    private static Runnable unwrapOriginal(Runnable r) {
        if (r instanceof PriorityRunnable) return ((PriorityRunnable) r).delegate;
        if (r instanceof TimedRunnable) return ((TimedRunnable) r).delegate;
        return r;
    }

    private String captureTraceId() {
        if (traceContextAdapter != null) {
            String traceId = traceContextAdapter.getTraceId();
            if (traceId != null) return traceId;
        }
        if (traceIdSupplier != null) {
            return traceIdSupplier.get();
        }
        return null;
    }

    private static class ExecutionRecord {
        final long executionTimeMs;
        final long waitTimeMs;
        final boolean success;

        ExecutionRecord(long executionTimeMs, boolean success, long waitTimeMs) {
            this.executionTimeMs = executionTimeMs;
            this.success = success;
            this.waitTimeMs = waitTimeMs;
        }
    }

    private static class MetricsRejectedHandler implements RejectedExecutionHandler {
        private volatile KsetThreadPoolExecutor pool;
        private final RejectedExecutionHandler delegate;
        private final String delegateName;

        MetricsRejectedHandler(RejectedExecutionHandler delegate) {
            this.delegate = delegate;
            this.delegateName = delegate.getClass().getSimpleName();
        }

        void attachPool(KsetThreadPoolExecutor pool) {
            this.pool = pool;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            KsetThreadPoolExecutor p = pool;
            if (p != null) {
                long rejected = p.rejectedCounter.incrementAndGet();
                String taskDesc = formatTaskDescription(r);
                boolean isCallerRuns = delegate instanceof CallerRunsPolicy;
                String msg = String.format(
                        "[Pool-%s] Task REJECTED by %s | task=%s | poolSize=%d/%d | active=%d | queue=%d/%d | completed=%d | totalRejected=%d",
                        p.poolName, delegateName, taskDesc,
                        executor.getPoolSize(), executor.getMaximumPoolSize(),
                        executor.getActiveCount(),
                        executor.getQueue().size(),
                        executor.getQueue().size() + executor.getQueue().remainingCapacity(),
                        executor.getCompletedTaskCount(),
                        rejected);
                if (isCallerRuns) {
                    log.info(msg + " [CallerRuns: task will execute in caller thread]");
                } else {
                    log.warn(msg + " [Task will be discarded or exception thrown]");
                }
                if (p.reporter != null) {
                    String traceId = getTraceId(r);
                    p.reporter.onTaskRejected(p.poolName, traceId, unwrapOriginal(r), delegateName);
                }
            }
            delegate.rejectedExecution(r, executor);
        }

        private static String formatTaskDescription(Runnable r) {
            if (r instanceof PriorityRunnable) {
                PriorityRunnable pr = (PriorityRunnable) r;
                return String.format("PriorityRunnable(priority=%d,seq=%d,delegate=%s)",
                        pr.priority, pr.sequence, pr.delegate.getClass().getSimpleName());
            }
            if (r instanceof PriorityFutureTask) {
                PriorityFutureTask<?> pf = (PriorityFutureTask<?>) r;
                return String.format("PriorityFutureTask(priority=%d,seq=%d)", pf.priority, pf.sequence);
            }
            if (r instanceof TimedRunnable) {
                return "TimedRunnable(delegate=" + ((TimedRunnable) r).delegate.getClass().getSimpleName() + ")";
            }
            if (r instanceof TimedFutureTask) {
                return "TimedFutureTask";
            }
            return r.getClass().getSimpleName();
        }
    }

    // ========== 队列工厂 ==========

    private static BlockingQueue<Runnable> createQueue(int capacity, boolean priorityQueueEnabled) {
        if (priorityQueueEnabled) {
            return new BoundedPriorityBlockingQueue<>(capacity, (r1, r2) -> {
                int p1 = getPriority(r1);
                int p2 = getPriority(r2);
                if (p1 != p2) {
                    return Integer.compare(p2, p1);
                }
                long s1 = getSequence(r1);
                long s2 = getSequence(r2);
                return Long.compare(s1, s2);
            });
        }
        return new ArrayBlockingQueue<>(capacity);
    }

    private static int getPriority(Runnable r) {
        if (r instanceof PriorityRunnable) return ((PriorityRunnable) r).priority;
        if (r instanceof PriorityFutureTask) return ((PriorityFutureTask<?>) r).priority;
        return 0;
    }

    private static long getSequence(Runnable r) {
        if (r instanceof PriorityRunnable) return ((PriorityRunnable) r).sequence;
        if (r instanceof PriorityFutureTask) return ((PriorityFutureTask<?>) r).sequence;
        return 0;
    }

    // ========== 构造函数（私有，通过 Builder 创建） ==========

    private KsetThreadPoolExecutor(String poolName, int corePoolSize, int maximumPoolSize,
                                    long keepAliveTimeMs, int queueCapacity,
                                    ThreadFactory threadFactory, RejectedExecutionHandler handler,
                                    long targetLatencyMs, boolean autoTuneEnabled, long tuneIntervalMs,
                                    int windowSize, boolean priorityQueueEnabled, int defaultPriority,
                                    ThreadPoolReporter reporter,
                                    java.util.function.Supplier<String> traceIdSupplier,
                                    ThreadPoolTraceAdapter traceContextAdapter) {
        super(corePoolSize, maximumPoolSize, keepAliveTimeMs, TimeUnit.MILLISECONDS,
              createQueue(queueCapacity, priorityQueueEnabled), threadFactory,
              new MetricsRejectedHandler(handler));
        MetricsRejectedHandler metricsHandler = (MetricsRejectedHandler) getRejectedExecutionHandler();
        metricsHandler.attachPool(this);

        this.poolName = poolName;
        this.targetLatencyMs = targetLatencyMs;
        this.autoTuneEnabled = autoTuneEnabled;
        this.tuneIntervalMs = tuneIntervalMs;
        this.windowSize = windowSize;
        this.windowBuffer = new ExecutionRecord[windowSize];
        this.priorityQueueEnabled = priorityQueueEnabled;
        this.defaultPriority = defaultPriority;
        this.initialMaximumPoolSize = maximumPoolSize;
        this.reporter = reporter;
        this.traceIdSupplier = traceIdSupplier;
        this.traceContextAdapter = traceContextAdapter;

        if (autoTuneEnabled) {
            startTuner();
        }

        log.info(String.format("[Pool-%s] Initialized: core=%d, max=%d, queue=%d, keepAlive=%dms, targetLatency=%dms, autoTune=%b, priorityQueue=%b, reporter=%b, traceAdapter=%b",
                 poolName, corePoolSize, maximumPoolSize, queueCapacity, keepAliveTimeMs, targetLatencyMs, autoTuneEnabled, priorityQueueEnabled, reporter != null, traceContextAdapter != null));
    }

    // ========== 工厂方法 ==========

    /**
     * 获取默认全局单例（向后兼容）。
     * <p>默认配置：core=32, max=200, queue=100, targetLatency=1000ms, autoTune=false, priorityQueue=false</p>
     */
    public static KsetThreadPoolExecutor getInstance() {
        if (DEFAULT_INSTANCE == null) {
            synchronized (DEFAULT_LOCK) {
                if (DEFAULT_INSTANCE == null) {
                    ThreadFactory factory = new ThreadFactoryBuilder()
                            .setNameFormat("we-thread-pool-%d")
                            .build();
                    DEFAULT_INSTANCE = newBuilder("default")
                            .corePoolSize(DEFAULT_CORE_POOL_SIZE)
                            .maximumPoolSize(DEFAULT_MAX_POOL_SIZE)
                            .keepAliveTimeMs(DEFAULT_KEEP_ALIVE_MS)
                            .queueCapacity(DEFAULT_QUEUE_CAPACITY)
                            .threadFactory(factory)
                            .rejectedExecutionHandler(new CallerRunsPolicy())
                            .build();
                }
            }
        }
        return DEFAULT_INSTANCE;
    }

    public static Builder newBuilder(String poolName) {
        return new Builder(poolName);
    }

    
    public static Builder forIoBound(String poolName) {
        int processors = Runtime.getRuntime().availableProcessors();
        return newBuilder(poolName)
                .corePoolSize(processors * 2)
                .maximumPoolSize(Math.max(processors * 4, 32))
                .queueCapacity(500)
                .targetLatencyMs(100)
                .autoTuneEnabled(true);
    }

    
    public static Builder forCpuBound(String poolName) {
        int processors = Runtime.getRuntime().availableProcessors();
        return newBuilder(poolName)
                .corePoolSize(processors)
                .maximumPoolSize(processors + 1)
                .queueCapacity(50)
                .targetLatencyMs(50)
                .autoTuneEnabled(true);
    }

    /**
     * 低延迟场景预设（要求快速响应，宁可拒绝也不排队）。
     * <p>推荐：core = CPU, max = CPU * 8, 极小队列, CallerRunsPolicy 兜底</p>
     */
    public static Builder forLowLatency(String poolName) {
        int processors = Runtime.getRuntime().availableProcessors();
        return newBuilder(poolName)
                .corePoolSize(processors)
                .maximumPoolSize(Math.max(processors * 8, 16))
                .queueCapacity(10)
                .targetLatencyMs(10)
                .autoTuneEnabled(true)
                .rejectedExecutionHandler(new CallerRunsPolicy());
    }

    /**
     * 高吞吐场景预设（批量处理，允许一定排队）。
     * <p>推荐：core = CPU * 2, max = CPU * 4, 大队列, 自动调优</p>
     */
    public static Builder forHighThroughput(String poolName) {
        int processors = Runtime.getRuntime().availableProcessors();
        return newBuilder(poolName)
                .corePoolSize(processors * 2)
                .maximumPoolSize(Math.max(processors * 4, 32))
                .queueCapacity(2000)
                .targetLatencyMs(500)
                .autoTuneEnabled(true);
    }

    /**
     * 混合场景预设（通用型，平衡吞吐和延迟）。
     */
    public static Builder forMixed(String poolName) {
        int processors = Runtime.getRuntime().availableProcessors();
        return newBuilder(poolName)
                .corePoolSize(processors)
                .maximumPoolSize(Math.max(processors * 2, 16))
                .queueCapacity(200)
                .targetLatencyMs(200)
                .autoTuneEnabled(true);
    }

    // ========== Builder ==========

    public static class Builder {
        private final String poolName;
        private int corePoolSize = DEFAULT_CORE_POOL_SIZE;
        private int maximumPoolSize = DEFAULT_MAX_POOL_SIZE;
        private long keepAliveTimeMs = DEFAULT_KEEP_ALIVE_MS;
        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;
        private ThreadFactory threadFactory;
        private RejectedExecutionHandler rejectedExecutionHandler = new CallerRunsPolicy();
        private long targetLatencyMs = DEFAULT_TARGET_LATENCY_MS;
        private boolean autoTuneEnabled = false;
        private long tuneIntervalMs = DEFAULT_TUNE_INTERVAL_MS;
        private int windowSize = DEFAULT_WINDOW_SIZE;
        private boolean priorityQueueEnabled = false;
        private int defaultPriority = DEFAULT_PRIORITY;
        private ThreadPoolReporter reporter;
        private java.util.function.Supplier<String> traceIdSupplier;
        private ThreadPoolTraceAdapter traceContextAdapter;

        private Builder(String poolName) {
            this.poolName = poolName;
        }

        public Builder corePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
            return this;
        }

        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public Builder keepAliveTimeMs(long keepAliveTimeMs) {
            this.keepAliveTimeMs = keepAliveTimeMs;
            return this;
        }

        public Builder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder rejectedExecutionHandler(RejectedExecutionHandler handler) {
            this.rejectedExecutionHandler = handler;
            return this;
        }

        public Builder targetLatencyMs(long targetLatencyMs) {
            this.targetLatencyMs = targetLatencyMs;
            return this;
        }

        public Builder autoTuneEnabled(boolean autoTuneEnabled) {
            this.autoTuneEnabled = autoTuneEnabled;
            return this;
        }

        public Builder tuneIntervalMs(long tuneIntervalMs) {
            this.tuneIntervalMs = tuneIntervalMs;
            return this;
        }

        public Builder windowSize(int windowSize) {
            this.windowSize = windowSize;
            return this;
        }

        public Builder priorityQueue(boolean enabled) {
            this.priorityQueueEnabled = enabled;
            return this;
        }

        public Builder defaultPriority(int priority) {
            this.defaultPriority = priority;
            return this;
        }

        public Builder reporter(ThreadPoolReporter reporter) {
            this.reporter = reporter;
            return this;
        }

        public Builder traceIdSupplier(java.util.function.Supplier<String> traceIdSupplier) {
            this.traceIdSupplier = traceIdSupplier;
            return this;
        }

        public Builder traceContextAdapter(ThreadPoolTraceAdapter traceContextAdapter) {
            this.traceContextAdapter = traceContextAdapter;
            return this;
        }

        public KsetThreadPoolExecutor build() {
            if (threadFactory == null) {
                threadFactory = new ThreadFactoryBuilder()
                        .setNameFormat(poolName + "-%d")
                        .build();
            }
            return new KsetThreadPoolExecutor(poolName, corePoolSize, maximumPoolSize,
                    keepAliveTimeMs, queueCapacity, threadFactory, rejectedExecutionHandler,
                    targetLatencyMs, autoTuneEnabled, tuneIntervalMs, windowSize,
                    priorityQueueEnabled, defaultPriority, reporter, traceIdSupplier, traceContextAdapter);
        }
    }

    

    @Override
    public void execute(Runnable command) {
        if (isShutdown()) {
            throw new RejectedExecutionException("Pool " + poolName + " is shutdown");
        }
        if (priorityQueueEnabled) {
            execute(command, defaultPriority);
            return;
        }
        submittedCounter.incrementAndGet();
        String traceId = captureTraceId();
        KsetContextSnapshot contextSnapshot = KsetContext.capture();
        long submitTime = System.currentTimeMillis();
        if (reporter != null) {
            reporter.onTaskSubmitted(poolName, traceId, command);
        }
        if (log.isDebugEnabled()) {
            log.debug("[Pool-{}] Task submitted | traceId={} | task={}", poolName, traceId, command.getClass().getSimpleName());
        }
        if (command instanceof TimedFutureTask) {
            super.execute(command);
        } else {
            super.execute(new TimedRunnable(command, submitTime, traceId, contextSnapshot));
        }
    }

    /**
     * 提交带优先级的任务（仅当 priorityQueue=true 时可用）。
     *
     * @param command  任务
     * @param priority 优先级，数值越大优先级越高
     */
    public void execute(Runnable command, int priority) {
        if (!priorityQueueEnabled) {
            throw new IllegalStateException("Priority queue not enabled for pool " + poolName);
        }
        if (isShutdown()) {
            throw new RejectedExecutionException("Pool " + poolName + " is shutdown");
        }
        submittedCounter.incrementAndGet();
        String traceId = captureTraceId();
        KsetContextSnapshot contextSnapshot = KsetContext.capture();
        long submitTime = System.currentTimeMillis();
        long seq = sequenceGenerator.getAndIncrement();
        if (reporter != null) {
            reporter.onTaskSubmitted(poolName, traceId, command);
        }
        if (log.isDebugEnabled()) {
            log.debug("[Pool-{}] Task submitted | traceId={} | priority={} | task={}",
                    poolName, traceId, priority, command.getClass().getSimpleName());
        }
        if (command instanceof PriorityFutureTask) {
            super.execute(command);
        } else {
            super.execute(new PriorityRunnable(command, submitTime, priority, seq, traceId, contextSnapshot));
        }
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        String traceId = captureTraceId();
        KsetContextSnapshot contextSnapshot = KsetContext.capture();
        if (priorityQueueEnabled) {
            return new PriorityFutureTask<>(callable, System.currentTimeMillis(), defaultPriority, sequenceGenerator.getAndIncrement(), traceId, contextSnapshot);
        }
        return new TimedFutureTask<>(callable, System.currentTimeMillis(), traceId, contextSnapshot);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        String traceId = captureTraceId();
        KsetContextSnapshot contextSnapshot = KsetContext.capture();
        if (priorityQueueEnabled) {
            return new PriorityFutureTask<>(runnable, value, System.currentTimeMillis(), defaultPriority, sequenceGenerator.getAndIncrement(), traceId, contextSnapshot);
        }
        return new TimedFutureTask<>(runnable, value, System.currentTimeMillis(), traceId, contextSnapshot);
    }

    /**
     * 提交带优先级的 Callable 任务（仅当 priorityQueue=true 时可用）。
     */
    public <T> Future<T> submit(Callable<T> task, int priority) {
        if (!priorityQueueEnabled) {
            throw new IllegalStateException("Priority queue not enabled for pool " + poolName);
        }
        String traceId = captureTraceId();
        KsetContextSnapshot contextSnapshot = KsetContext.capture();
        RunnableFuture<T> ftask = new PriorityFutureTask<>(task, System.currentTimeMillis(), priority, sequenceGenerator.getAndIncrement(), traceId, contextSnapshot);
        execute(ftask);
        return ftask;
    }

    /**
     * 提交带优先级的 Runnable 任务（仅当 priorityQueue=true 时可用）。
     */
    public Future<?> submit(Runnable task, int priority) {
        if (!priorityQueueEnabled) {
            throw new IllegalStateException("Priority queue not enabled for pool " + poolName);
        }
        String traceId = captureTraceId();
        KsetContextSnapshot contextSnapshot = KsetContext.capture();
        RunnableFuture<Void> ftask = new PriorityFutureTask<>(task, null, System.currentTimeMillis(), priority, sequenceGenerator.getAndIncrement(), traceId, contextSnapshot);
        execute(ftask);
        return ftask;
    }

    /**
     * 提交带优先级的 Runnable 任务并指定返回值（仅当 priorityQueue=true 时可用）。
     */
    public <T> Future<T> submit(Runnable task, T result, int priority) {
        if (!priorityQueueEnabled) {
            throw new IllegalStateException("Priority queue not enabled for pool " + poolName);
        }
        String traceId = captureTraceId();
        KsetContextSnapshot contextSnapshot = KsetContext.capture();
        RunnableFuture<T> ftask = new PriorityFutureTask<>(task, result, System.currentTimeMillis(), priority, sequenceGenerator.getAndIncrement(), traceId, contextSnapshot);
        execute(ftask);
        return ftask;
    }

    // ========== beforeExecute / afterExecute 钩子 ==========

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        long start = System.currentTimeMillis();
        long waitTime = 0;

        if (r instanceof PriorityRunnable) {
            PriorityRunnable pr = (PriorityRunnable) r;
            waitTime = start - pr.submitTime;
            Runnable delegate = pr.delegate;
            if (delegate instanceof FutureTask) {
                currentFutureTask.set((FutureTask<?>) delegate);
            } else {
                currentFutureTask.remove();
            }
        } else if (r instanceof PriorityFutureTask) {
            PriorityFutureTask<?> task = (PriorityFutureTask<?>) r;
            currentFutureTask.set(task);
            waitTime = start - task.submitTime;
        } else if (r instanceof TimedFutureTask) {
            TimedFutureTask<?> task = (TimedFutureTask<?>) r;
            currentFutureTask.set(task);
            waitTime = start - task.submitTime;
        } else if (r instanceof TimedRunnable) {
            currentFutureTask.remove();
            waitTime = start - ((TimedRunnable) r).submitTime;
        }

        currentContextScope.set(KsetContext.openScope(getContextSnapshot(r)));
        String traceId = getTraceId(r);
        if (traceContextAdapter != null && traceId != null) {
            traceContextAdapter.setTraceId(traceId);
        }
        if (reporter != null) {
            reporter.onTaskStarted(poolName, traceId, unwrapOriginal(r), waitTime);
        }

        startTimeHolder.set(start);
        waitTimeHolder.set(waitTime);
        super.beforeExecute(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        String traceId = getTraceId(r);
        try {
            Long start = startTimeHolder.get();
            if (start != null) {
                long executionTime = System.currentTimeMillis() - start;

                boolean success = (t == null);
                Runnable delegate = null;

                if (r instanceof PriorityRunnable) {
                    delegate = ((PriorityRunnable) r).delegate;
                }

                if (delegate instanceof FutureTask) {
                    FutureTask<?> task = (FutureTask<?>) delegate;
                    if (task.isDone() && !task.isCancelled()) {
                        try {
                            task.get();
                        } catch (ExecutionException ee) {
                            success = false;
                        } catch (InterruptedException | CancellationException e) {
                            // ignore
                        }
                    }
                } else if (r instanceof PriorityFutureTask) {
                    PriorityFutureTask<?> task = (PriorityFutureTask<?>) r;
                    if (task.isDone() && !task.isCancelled()) {
                        try {
                            task.get();
                        } catch (ExecutionException ee) {
                            success = false;
                        } catch (InterruptedException | CancellationException e) {
                            // ignore
                        }
                    }
                } else if (r instanceof TimedFutureTask) {
                    TimedFutureTask<?> task = (TimedFutureTask<?>) r;
                    if (task.isDone() && !task.isCancelled()) {
                        try {
                            task.get();
                        } catch (ExecutionException ee) {
                            success = false;
                        } catch (InterruptedException | CancellationException e) {
                            // ignore
                        }
                    }
                }

                if (!success) {
                    failedCounter.incrementAndGet();
                }

                Long wait = waitTimeHolder.get();
                long waitTime = wait != null ? wait : 0;

                recordExecution(executionTime, success, waitTime);
                completedCounter.incrementAndGet();

                if (reporter != null) {
                    reporter.onTaskCompleted(poolName, traceId, unwrapOriginal(r), executionTime, success);
                }
            }
            if (t != null && reporter != null) {
                reporter.onError(poolName, traceId, unwrapOriginal(r), t);
            }
        } finally {
            startTimeHolder.remove();
            waitTimeHolder.remove();
            currentFutureTask.remove();
            KsetContextScope contextScope = currentContextScope.get();
            currentContextScope.remove();
            if (contextScope != null) {
                contextScope.close();
            }
            if (traceContextAdapter != null) {
                traceContextAdapter.clear();
            }
            super.afterExecute(r, t);
        }
    }

    // ========== 指标记录与查询 ==========

    private void recordExecution(long executionTimeMs, boolean success, long waitTimeMs) {
        long idx = windowWritePos.getAndIncrement();
        int pos = (int) (idx % windowSize);
        windowBuffer[pos] = new ExecutionRecord(executionTimeMs, success, waitTimeMs);
    }

    
    public ThreadPoolMetrics getMetrics() {
        updateThroughput();

        long totalExecTime = 0;
        long totalWaitTime = 0;
        long maxExec = Long.MIN_VALUE;
        long minExec = Long.MAX_VALUE;
        int count = 0;
        int successCount = 0;

        for (int i = 0; i < windowSize; i++) {
            ExecutionRecord rec = windowBuffer[i];
            if (rec != null) {
                count++;
                totalExecTime += rec.executionTimeMs;
                totalWaitTime += rec.waitTimeMs;
                if (rec.executionTimeMs > maxExec) maxExec = rec.executionTimeMs;
                if (rec.executionTimeMs < minExec) minExec = rec.executionTimeMs;
                if (rec.success) successCount++;
            }
        }

        double avgExec = count > 0 ? (double) totalExecTime / count : 0;
        double avgWait = count > 0 ? (double) totalWaitTime / count : 0;
        double successRate = count > 0 ? (double) successCount / count : 1.0;

        double p99 = 0;
        if (count > 0) {
            // 使用 windowSize 分配数组，避免并发写入导致越界（afterExecute 可能在两次遍历间写入新记录）
            long[] times = new long[windowSize];
            int idx = 0;
            for (int i = 0; i < windowSize; i++) {
                ExecutionRecord rec = windowBuffer[i];
                if (rec != null) {
                    times[idx++] = rec.executionTimeMs;
                }
            }
            if (idx > 0) {
                times = Arrays.copyOf(times, idx);
                Arrays.sort(times);
                int p99Index = (int) Math.ceil(idx * 0.99) - 1;
                p99Index = Math.max(0, Math.min(p99Index, idx - 1));
                p99 = times[p99Index];
            }
        }

        long submitted = submittedCounter.get();
        long rejected = rejectedCounter.get();
        double latencyErrorRatio = targetLatencyMs > 0
                ? (avgExec - targetLatencyMs) / (double) targetLatencyMs
                : 0;

        BlockingQueue<Runnable> queue = getQueue();
        int queueCapacity = queue.size() + queue.remainingCapacity();

        return ThreadPoolMetrics.builder()
                .poolName(poolName)
                .corePoolSize(getCorePoolSize())
                .maximumPoolSize(getMaximumPoolSize())
                .keepAliveTimeMs(getKeepAliveTime(TimeUnit.MILLISECONDS))
                .queueCapacity(queueCapacity)
                .autoTuneEnabled(autoTuneEnabled)
                .targetLatencyMs(targetLatencyMs)
                .priorityQueueEnabled(priorityQueueEnabled)
                .defaultPriority(defaultPriority)
                .poolSize(getPoolSize())
                .activeCount(getActiveCount())
                .largestPoolSize(getLargestPoolSize())
                .queueSize(queue.size())
                .queueRemainingCapacity(queue.remainingCapacity())
                .submittedTasks(submitted)
                .completedTasks(completedCounter.get())
                .failedTasks(failedCounter.get())
                .rejectedTasks(rejected)
                .successRate(successRate)
                .avgExecutionTimeMs(avgExec)
                .avgWaitTimeMs(avgWait)
                .maxExecutionTimeMs(maxExec == Long.MIN_VALUE ? 0 : maxExec)
                .minExecutionTimeMs(minExec == Long.MAX_VALUE ? 0 : minExec)
                .p99ExecutionTimeMs(p99)
                .throughputPerSecond(cachedThroughput)
                .latencyErrorRatio(latencyErrorRatio)
                .lastTuneTimeMs(lastTuneTimeMs)
                .lastTuneAction(lastTuneAction.get())
                .build();
    }

    private void updateThroughput() {
        long now = System.currentTimeMillis();
        long lastTime = lastSnapshotTime.getAndSet(now);
        long lastCompleted = lastCompletedSnapshot.getAndSet(completedCounter.get());
        long elapsed = now - lastTime;
        if (elapsed > 0) {
            long delta = completedCounter.get() - lastCompleted;
            cachedThroughput = delta * 1000.0 / elapsed;
        }
    }

    // ========== 上报器配置 ==========

    public synchronized void setReporter(ThreadPoolReporter reporter) {
        this.reporter = reporter;
        log.info(String.format("[Pool-%s] Reporter updated: %b", poolName, reporter != null));
    }

    public ThreadPoolReporter getReporter() {
        return reporter;
    }

    public void setTraceIdSupplier(java.util.function.Supplier<String> traceIdSupplier) {
        this.traceIdSupplier = traceIdSupplier;
    }

    public void setTraceContextAdapter(ThreadPoolTraceAdapter traceContextAdapter) {
        this.traceContextAdapter = traceContextAdapter;
    }

    public ThreadPoolTraceAdapter getTraceContextAdapter() {
        return traceContextAdapter;
    }

    /**
     * 手动触发指标全量上报。
     */
    public void reportMetrics() {
        ThreadPoolMetrics metrics = getMetrics();
        if (reporter != null) {
            String traceId = traceContextAdapter != null ? traceContextAdapter.getTraceId() : null;
            reporter.onMetricsReport(poolName, traceId, metrics);
        }
    }

    // ========== 动态配置接口 ==========

    public synchronized void setTargetLatencyMs(long targetLatencyMs) {
        this.targetLatencyMs = targetLatencyMs;
        log.info(String.format("[Pool-%s] Target latency updated: %dms", poolName, targetLatencyMs));
    }

    public synchronized void setAutoTuneEnabled(boolean enabled) {
        if (this.autoTuneEnabled == enabled) {
            return;
        }
        this.autoTuneEnabled = enabled;
        if (enabled) {
            startTuner();
        } else {
            stopTuner();
        }
        log.info(String.format("[Pool-%s] Auto-tune: %b", poolName, enabled));
    }

    public synchronized void setTuneIntervalMs(long intervalMs) {
        this.tuneIntervalMs = Math.max(1000, intervalMs);
        if (autoTuneEnabled) {
            stopTuner();
            startTuner();
        }
    }

    @Override
    public void setCorePoolSize(int corePoolSize) {
        super.setCorePoolSize(corePoolSize);
        log.info(String.format("[Pool-%s] Core pool size set to: %d", poolName, corePoolSize));
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
        super.setMaximumPoolSize(maximumPoolSize);
        log.info(String.format("[Pool-%s] Max pool size set to: %d", poolName, maximumPoolSize));
    }

    
    public void setKeepAliveTimeMs(long keepAliveTimeMs) {
        super.setKeepAliveTime(keepAliveTimeMs, TimeUnit.MILLISECONDS);
        log.info(String.format("[Pool-%s] Keep alive time set to: %dms", poolName, keepAliveTimeMs));
    }

    /**
     * 设置默认优先级（仅当 priorityQueue=true 时有效）。
     */
    public synchronized void setDefaultPriority(int defaultPriority) {
        this.defaultPriority = defaultPriority;
        log.info(String.format("[Pool-%s] Default priority set to: %d", poolName, defaultPriority));
    }

    // ========== 自动调节器（控制论核心） ==========

    private void startTuner() {
        if (tuner != null && !tuner.isShutdown()) {
            return;
        }
        tuner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, poolName + "-tuner");
            t.setDaemon(true);
            return t;
        });
        tuner.scheduleWithFixedDelay(this::tune, tuneIntervalMs, tuneIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void stopTuner() {
        if (tuner != null) {
            tuner.shutdownNow();
            tuner = null;
        }
    }

    private void tune() {
        if (isShutdown()) {
            return;
        }
        try {
            ThreadPoolMetrics metrics = getMetrics();
            int currentCore = getCorePoolSize();
            int currentMax = getMaximumPoolSize();
            int active = getActiveCount();
            int queueSize = getQueue().size();
            int queueCapacity = metrics.getQueueCapacity();
            double queueUsage = queueCapacity > 0 ? (double) queueSize / queueCapacity : 0;

            long target = this.targetLatencyMs;
            if (target <= 0) {
                return;
            }

            double actualLatency = metrics.getAvgExecutionTimeMs();
            double latencyErrorRatio = (actualLatency - target) / (double) target;

            StringBuilder action = new StringBuilder();

            // Core pool size adjustment based on latency
            if (latencyErrorRatio > DEAD_ZONE_RATIO) {
                int delta = computeDelta(latencyErrorRatio, currentCore);
                int newCore = Math.min(currentCore + delta, currentMax);
                if (newCore > currentCore) {
                    super.setCorePoolSize(newCore);
                    action.append("expand_core:").append(currentCore).append("->").append(newCore);
                }
            } else if (latencyErrorRatio < -DEAD_ZONE_RATIO) {
                if (queueUsage < SHRINK_QUEUE_THRESHOLD && active < currentCore * 0.5) {
                    int delta = computeDelta(Math.abs(latencyErrorRatio), currentCore);
                    int newCore = Math.max(currentCore - delta, 1);
                    if (newCore < currentCore) {
                        super.setCorePoolSize(newCore);
                        action.append("shrink_core:").append(currentCore).append("->").append(newCore);
                    }
                }
            }

            // Windowed rejection rate for max pool adjustment (not cumulative)
            long currentSubmitted = submittedCounter.get();
            long currentRejected = rejectedCounter.get();
            long deltaSubmitted = currentSubmitted - lastTuneSubmitted;
            long deltaRejected = currentRejected - lastTuneRejected;
            double windowRejectionRate = deltaSubmitted > 0 ? (double) deltaRejected / deltaSubmitted : 0;

            int maxPoolCap = (int) Math.min(initialMaximumPoolSize * 4L, Integer.MAX_VALUE);

            // Expand max if recent rejection rate is high
            if (windowRejectionRate > REJECTION_RATE_THRESHOLD && currentMax < maxPoolCap) {
                int newMax = (int) (currentMax * MAX_POOL_EXPAND_RATIO);
                newMax = Math.min(newMax, maxPoolCap);
                if (newMax > currentMax) {
                    super.setMaximumPoolSize(newMax);
                    if (action.length() > 0) {
                        action.append(", ");
                    }
                    action.append("expand_max:").append(currentMax).append("->").append(newMax);
                }
            }

            // Shrink max if underutilized and was previously expanded
            if (latencyErrorRatio < -DEAD_ZONE_RATIO
                    && queueUsage < SHRINK_QUEUE_THRESHOLD
                    && active < currentCore * 0.5
                    && currentMax > initialMaximumPoolSize) {
                int newMax = Math.max((int) (currentMax / MAX_POOL_EXPAND_RATIO), initialMaximumPoolSize);
                if (newMax < currentMax) {
                    super.setMaximumPoolSize(newMax);
                    if (action.length() > 0) {
                        action.append(", ");
                    }
                    action.append("shrink_max:").append(currentMax).append("->").append(newMax);
                }
            }

            // Update differential snapshots
            lastTuneSubmitted = currentSubmitted;
            lastTuneRejected = currentRejected;

            lastTuneTimeMs = System.currentTimeMillis();
            lastTuneAction.set(action.length() > 0 ? action.toString() : "no_action");

            if (action.length() > 0) {
                log.info(String.format("[Pool-%s] Auto-tuned: %s", poolName, action));
            }
            if (reporter != null) {
                String traceId = traceContextAdapter != null ? traceContextAdapter.getTraceId() : null;
                reporter.onAutoTuned(poolName, traceId, lastTuneAction.get(), metrics);
            }
        } catch (Exception e) {
            log.error("[Pool-{}] Auto-tune error", poolName, e);
        }
    }

    private int computeDelta(double errorRatio, int currentSize) {
        int delta = (int) Math.ceil(Math.abs(errorRatio) * currentSize * 0.1);
        return Math.max(MIN_SINGLE_DELTA, Math.min(MAX_SINGLE_DELTA, delta));
    }

    // ========== 生命周期 ==========

    @Override
    public void shutdown() {
        stopTuner();
        super.shutdown();
        log.info(String.format("[Pool-%s] Shutdown initiated", poolName));
    }

    @Override
    public List<Runnable> shutdownNow() {
        stopTuner();
        List<Runnable> remaining = super.shutdownNow();
        log.info(String.format("[Pool-%s] ShutdownNow, %d tasks returned", poolName, remaining.size()));
        return remaining;
    }

    @Override
    public String toString() {
        return getMetrics().toJson();
    }
}
