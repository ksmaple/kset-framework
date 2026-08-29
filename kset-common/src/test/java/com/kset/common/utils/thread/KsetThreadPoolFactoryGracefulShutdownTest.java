package com.kset.common.utils.thread;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工厂优雅停机：预算内的池排空完成，超时的池 shutdownNow 兜底中断。
 *
 * <p>工厂为全局单例且全局关闭不可逆，故全部场景合并在单个用例中。</p>
 */
class KsetThreadPoolFactoryGracefulShutdownTest {

    @Test
    void shutdownAllDrainsWithinTimeoutAndForcesOnTimeout() {
        KsetThreadPoolFactory factory = KsetThreadPoolFactory.getInstance();
        AtomicBoolean quickDone = new AtomicBoolean();
        AtomicBoolean longInterrupted = new AtomicBoolean();

        factory.execute("graceful-quick", () -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            quickDone.set(true);
        });
        factory.execute("graceful-long", () -> {
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                longInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        factory.shutdownAll(1_000);

        assertThat(quickDone).as("预算内任务应排空完成").isTrue();
        assertThat(longInterrupted).as("超时任务应被 shutdownNow 中断").isTrue();
        assertThat(factory.getAllMetrics()).as("关闭后池应清空").isEmpty();
    }
}
