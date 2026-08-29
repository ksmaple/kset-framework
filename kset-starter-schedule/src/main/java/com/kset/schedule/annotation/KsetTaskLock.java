package com.kset.schedule.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 唯一运行锁：声明后全集群同一时刻只有一个实例执行该方法，抢不到锁的实例直接跳过。
 *
 * <p>仅需要唯一运行的任务才声明，与调度注解解耦：</p>
 * <ul>
 *   <li>叠加在 {@link KsetScheduled} 上：集群单活的定时任务</li>
 *   <li>单独标注在任意方法上：全局唯一执行语义（如手动补偿入口）</li>
 * </ul>
 *
 * <p>存储为 SQL 锁表（{@code t_kset_schedule_lock}），抢不到锁时方法不执行并返回 {@code null}。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KsetTaskLock {

    /**
     * 锁名，空则默认为 {@code 类名.方法名}。
     */
    String name() default "";

    /**
     * 宕机兜底释放时间（如 {@code 10m}、{@code 40s}，ISO-8601 或 Duration 风格）。
     * 必须大于任务最长执行时间；空则用全局默认 {@code kset.scheduler.lock.at-most-for}（10m）。
     */
    String atMostFor() default "";

    /**
     * 执行完毕后锁至少保留的时间，防止秒级任务因时钟漂移在集群内接力重复执行。
     * 周期 ≥5 分钟的任务保持 {@code 0s} 即可。
     */
    String atLeastFor() default "0s";
}
