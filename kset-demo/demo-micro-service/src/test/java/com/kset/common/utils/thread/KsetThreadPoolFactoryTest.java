package com.kset.common.utils.thread;

import com.kset.common.monitor.Monitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KsetThreadPoolFactory 测试用例。
 */
public class KsetThreadPoolFactoryTest {

    private KsetThreadPoolFactory factory;

    @BeforeEach
    public void setUp() {
        factory = KsetThreadPoolFactory.getInstance();
    }

    @AfterEach
    public void tearDown() throws Exception {
        factory.shutdownAll();
        Monitor.clear();
        // 反射重置 globalShutdown，避免单例状态污染后续测试
        java.lang.reflect.Field field = KsetThreadPoolFactory.class.getDeclaredField("globalShutdown");
        field.setAccessible(true);
        java.util.concurrent.atomic.AtomicBoolean shutdown = (java.util.concurrent.atomic.AtomicBoolean) field.get(factory);
        shutdown.set(false);
    }

    // ========== 基础功能 ==========

    @Test
    public void testSingleton() {
        KsetThreadPoolFactory f1 = KsetThreadPoolFactory.getInstance();
        KsetThreadPoolFactory f2 = KsetThreadPoolFactory.getInstance();
        assertSame(f1, f2);
    }

    @Test
    public void testLazyCreateDefault() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        factory.execute("test-lazy-default", () -> {
            counter.incrementAndGet();
            latch.countDown();
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(1, counter.get());

        ThreadPoolMetrics metrics = factory.getMetrics("test-lazy-default");
        assertNotNull(metrics);
        assertEquals("test-lazy-default", metrics.getPoolName());
        assertTrue(metrics.getCorePoolSize() > 0);
    }

    @Test
    public void testRegisterAndExecute() throws Exception {
        factory.register("test-register",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(2)
                .maximumPoolSize(4)
                .queueCapacity(10)
                .targetLatencyMs(100)
                .build());

        AtomicInteger counter = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            factory.execute("test-register", () -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(3, counter.get());

        ThreadPoolMetrics metrics = factory.getMetrics("test-register");
        assertEquals(2, metrics.getCorePoolSize());
        assertEquals(4, metrics.getMaximumPoolSize());
    }

    // ========== 提交方式 ==========

    @Test
    public void testSubmitRunnable() throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        Future<?> future = factory.submit("test-submit-run", counter::incrementAndGet);
        future.get(3, TimeUnit.SECONDS);
        assertEquals(1, counter.get());
    }

    @Test
    public void testSubmitCallable() throws Exception {
        Future<String> future = factory.submit("test-submit-call", () -> "hello");
        String result = future.get(3, TimeUnit.SECONDS);
        assertEquals("hello", result);
    }

    @Test
    public void testExecuteWithPriority() throws Exception {
        factory.register("test-priority",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(1)
                .maximumPoolSize(1)
                .queueCapacity(10)
                .priorityQueue(true)
                .defaultPriority(5)
                .build());

        // 先发 3 个低优先级任务塞满队列
        CountDownLatch lowLatch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            factory.execute("test-priority", lowLatch::countDown, 1);
        }

        // 再发 1 个高优先级任务
        AtomicInteger highExecuted = new AtomicInteger(0);
        CountDownLatch highLatch = new CountDownLatch(1);
        factory.execute("test-priority", () -> {
            highExecuted.incrementAndGet();
            highLatch.countDown();
        }, 10);

        assertTrue(highLatch.await(3, TimeUnit.SECONDS));
        assertEquals(1, highExecuted.get());
        assertTrue(lowLatch.await(3, TimeUnit.SECONDS));
    }

    // ========== 业务隔离 ==========

    @Test
    public void testBizIsolation() throws Exception {
        factory.register("test-biz-a",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(2)
                .maximumPoolSize(4)
                .queueCapacity(10)
                .build());

        factory.register("test-biz-b",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(4)
                .maximumPoolSize(8)
                .queueCapacity(20)
                .build());

        CountDownLatch latchA = new CountDownLatch(1);
        CountDownLatch latchB = new CountDownLatch(1);

        factory.execute("test-biz-a", latchA::countDown);
        factory.execute("test-biz-b", latchB::countDown);

        assertTrue(latchA.await(3, TimeUnit.SECONDS));
        assertTrue(latchB.await(3, TimeUnit.SECONDS));

        ThreadPoolMetrics metricsA = factory.getMetrics("test-biz-a");
        ThreadPoolMetrics metricsB = factory.getMetrics("test-biz-b");

        assertNotNull(metricsA);
        assertNotNull(metricsB);
        assertEquals(2, metricsA.getCorePoolSize());
        assertEquals(4, metricsB.getCorePoolSize());
    }

    // ========== 动态调参 ==========

    @Test
    public void testDynamicCorePoolSize() {
        factory.register("test-dynamic",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(2)
                .maximumPoolSize(10)
                .queueCapacity(10)
                .build());

        // 先触发创建
        factory.execute("test-dynamic", () -> {});

        factory.setCorePoolSize("test-dynamic", 6);
        ThreadPoolMetrics metrics = factory.getMetrics("test-dynamic");
        assertEquals(6, metrics.getCorePoolSize());
    }

    @Test
    public void testDynamicMaxPoolSize() {
        factory.register("test-dynamic-max",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(2)
                .maximumPoolSize(10)
                .queueCapacity(10)
                .build());

        factory.execute("test-dynamic-max", () -> {});

        factory.setMaximumPoolSize("test-dynamic-max", 20);
        ThreadPoolMetrics metrics = factory.getMetrics("test-dynamic-max");
        assertEquals(20, metrics.getMaximumPoolSize());
    }

    @Test
    public void testDynamicTargetLatency() {
        factory.register("test-dynamic-latency",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(2)
                .maximumPoolSize(10)
                .queueCapacity(10)
                .targetLatencyMs(500)
                .build());

        factory.execute("test-dynamic-latency", () -> {});

        factory.setTargetLatencyMs("test-dynamic-latency", 200);
        ThreadPoolMetrics metrics = factory.getMetrics("test-dynamic-latency");
        assertEquals(200, metrics.getTargetLatencyMs());
    }

    // ========== 指标 ==========

    @Test
    public void testMetrics() throws Exception {
        factory.register("test-metrics",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(2)
                .maximumPoolSize(4)
                .queueCapacity(10)
                .build());

        CountDownLatch latch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            factory.execute("test-metrics", () -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        // 等待指标统计
        Thread.sleep(100);

        ThreadPoolMetrics metrics = factory.getMetrics("test-metrics");
        assertNotNull(metrics);
        assertEquals("test-metrics", metrics.getPoolName());
        assertEquals(2, metrics.getCorePoolSize());
        assertEquals(4, metrics.getMaximumPoolSize());
        assertTrue(metrics.getSubmittedTasks() >= 5);
        assertTrue(metrics.getCompletedTasks() >= 5);
        assertTrue(metrics.getAvgExecutionTimeMs() >= 0);
    }

    @Test
    public void testAllMetrics() {
        factory.register("test-all-1",
            KsetThreadPoolFactory.PoolConfig.builder().corePoolSize(1).maximumPoolSize(2).queueCapacity(5).build());
        factory.register("test-all-2",
            KsetThreadPoolFactory.PoolConfig.builder().corePoolSize(1).maximumPoolSize(2).queueCapacity(5).build());

        factory.execute("test-all-1", () -> {});
        factory.execute("test-all-2", () -> {});

        java.util.Map<String, ThreadPoolMetrics> all = factory.getAllMetrics();
        assertNotNull(all);
        assertTrue(all.containsKey("test-all-1"));
        assertTrue(all.containsKey("test-all-2"));
    }

    // ========== 场景预设 ==========

    @Test
    public void testIoConfig() {
        KsetThreadPoolFactory.PoolConfig config = KsetThreadPoolFactory.PoolConfig.ioConfig();
        assertTrue(config.getCorePoolSize() > 0);
        assertTrue(config.getMaximumPoolSize() >= config.getCorePoolSize());
        assertTrue(config.isAutoTuneEnabled());
    }

    @Test
    public void testCpuConfig() {
        KsetThreadPoolFactory.PoolConfig config = KsetThreadPoolFactory.PoolConfig.cpuConfig();
        assertTrue(config.getCorePoolSize() > 0);
        assertTrue(config.getMaximumPoolSize() >= config.getCorePoolSize());
        assertTrue(config.isAutoTuneEnabled());
    }

    @Test
    public void testLowLatencyConfig() {
        KsetThreadPoolFactory.PoolConfig config = KsetThreadPoolFactory.PoolConfig.lowLatencyConfig();
        assertTrue(config.isPriorityQueue());
        assertNotNull(config.getRejectedHandler());
    }

    // ========== 生命周期 ==========

    @Test
    public void testShutdown() {
        factory.register("test-shutdown",
            KsetThreadPoolFactory.PoolConfig.builder().corePoolSize(1).maximumPoolSize(2).queueCapacity(5).build());

        factory.execute("test-shutdown", () -> {});
        assertNotNull(factory.getMetrics("test-shutdown"));

        factory.shutdown("test-shutdown");
        // shutdown 后从 pools map 中移除，指标不可再获取
        assertNull(factory.getMetrics("test-shutdown"));
    }

    @Test
    public void testShutdownAll() {
        factory.register("test-shutdown-all",
            KsetThreadPoolFactory.PoolConfig.builder().corePoolSize(1).maximumPoolSize(2).queueCapacity(5).build());

        factory.shutdownAll();
        assertThrows(RejectedExecutionException.class,
                () -> factory.execute("test-shutdown-all", () -> {}));
    }

    @Test
    public void testRegisterNullConfig() {
        assertThrows(IllegalArgumentException.class, () -> factory.register("test-null", null));
    }

    @Test
    public void testMetricsNotExist() {
        ThreadPoolMetrics metrics = factory.getMetrics("non-exist-biz");
        assertNull(metrics);
    }

    // ========== 上报器 ==========

    @Test
    public void testReporter() throws Exception {
        java.util.concurrent.atomic.AtomicInteger submitted = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger started = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicReference<String> lastPoolName = new java.util.concurrent.atomic.AtomicReference<>();

        ThreadPoolReporter reporter = new ThreadPoolReporter() {
            @Override public void onTaskSubmitted(String poolName, String traceId, Runnable task) {
                lastPoolName.set(poolName);
                submitted.incrementAndGet();
            }
            @Override public void onTaskStarted(String poolName, String traceId, Runnable task, long waitTimeMs) {
                started.incrementAndGet();
            }
            @Override public void onTaskCompleted(String poolName, String traceId, Runnable task, long executionTimeMs, boolean success) {
                completed.incrementAndGet();
            }
            @Override public void onTaskRejected(String poolName, String traceId, Runnable task, String policyName) {}
            @Override public void onAutoTuned(String poolName, String traceId, String action, ThreadPoolMetrics metrics) {}
            @Override public void onError(String poolName, String traceId, Runnable task, Throwable t) {}
            @Override public void onMetricsReport(String poolName, String traceId, ThreadPoolMetrics metrics) {}
        };

        factory.register("test-reporter",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(1).maximumPoolSize(2).queueCapacity(5)
                .reporter(reporter)
                .build());

        CountDownLatch latch = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            factory.execute("test-reporter", latch::countDown);
        }
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        // 等待 afterExecute 完成
        Thread.sleep(100);
        assertEquals(2, submitted.get());
        assertEquals(2, started.get());
        assertEquals(2, completed.get());
        assertEquals("test-reporter", lastPoolName.get());
    }

    // ========== TraceId 上下文传递 ==========

    @Test
    public void testThreadPoolTraceAdapter() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> capturedTraceId = new java.util.concurrent.atomic.AtomicReference<>();

        ThreadPoolTraceAdapter adapter = new ThreadPoolTraceAdapter() {
            private final ThreadLocal<String> holder = new ThreadLocal<>();
            @Override public String getTraceId() { return holder.get(); }
            @Override public void setTraceId(String traceId) { holder.set(traceId); }
            @Override public void clear() { holder.remove(); }
        };

        factory.register("test-trace",
            KsetThreadPoolFactory.PoolConfig.builder()
                .corePoolSize(1).maximumPoolSize(2).queueCapacity(5)
                .traceContextAdapter(adapter)
                .build());

        
        adapter.setTraceId("trace-123");

        CountDownLatch latch = new CountDownLatch(1);
        factory.execute("test-trace", () -> {
            
            capturedTraceId.set(adapter.getTraceId());
            latch.countDown();
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals("trace-123", capturedTraceId.get());

        // 清理
        adapter.clear();
    }

    @Test
    public void testMdcThreadPoolTraceAdapter() {
        MdcThreadPoolTraceAdapter adapter = new MdcThreadPoolTraceAdapter();
        Monitor.clear();

        assertNull(adapter.getTraceId());
        adapter.setTraceId("test");
        assertEquals("test", adapter.getTraceId());
        adapter.clear();
        assertNull(adapter.getTraceId());
    }
}
