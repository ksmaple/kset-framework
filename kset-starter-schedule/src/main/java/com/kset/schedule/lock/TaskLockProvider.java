package com.kset.schedule.lock;

import java.time.Duration;

/**
 * 唯一运行锁提供器。抢锁成功返回 {@code true}，执行完毕后须调用 {@link #unlock}。
 */
public interface TaskLockProvider {

    /**
     * 尝试抢锁：锁不存在或已过期（lock_until 到期）时获得锁。
     *
     * @param name       锁名
     * @param atMostFor  宕机兜底释放时间（必须大于任务最长执行时间）
     * @return true 表示本实例获得锁，应执行任务
     */
    boolean tryLock(String name, Duration atMostFor);

    /**
     * 释放锁：将锁有效期缩短为 now + atLeastFor（最短持锁，防秒级任务集群接力）。
     */
    void unlock(String name, Duration atLeastFor);
}
