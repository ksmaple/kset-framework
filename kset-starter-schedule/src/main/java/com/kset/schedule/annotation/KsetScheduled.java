package com.kset.schedule.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * kset 受管定时任务：继承原生 {@link Scheduled} 的全部调度属性，使用即纳入 kset 任务管理。
 *
 * <p>与原生 {@code @Scheduled} 的区分约定：</p>
 * <ul>
 *   <li>{@code @Scheduled}：本机任务（如本地缓存刷新），不纳入管理，每实例都执行</li>
 *   <li>{@code @KsetScheduled}：受管任务，纳入任务注册表；叠加 {@link KsetTaskLock} 后集群唯一运行</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Scheduled
public @interface KsetScheduled {

    @AliasFor(annotation = Scheduled.class, attribute = "cron")
    String cron() default "";

    @AliasFor(annotation = Scheduled.class, attribute = "zone")
    String zone() default "";

    @AliasFor(annotation = Scheduled.class, attribute = "fixedDelay")
    long fixedDelay() default -1;

    @AliasFor(annotation = Scheduled.class, attribute = "fixedDelayString")
    String fixedDelayString() default "";

    @AliasFor(annotation = Scheduled.class, attribute = "fixedRate")
    long fixedRate() default -1;

    @AliasFor(annotation = Scheduled.class, attribute = "fixedRateString")
    String fixedRateString() default "";

    @AliasFor(annotation = Scheduled.class, attribute = "initialDelay")
    long initialDelay() default -1;

    @AliasFor(annotation = Scheduled.class, attribute = "initialDelayString")
    String initialDelayString() default "";

    @AliasFor(annotation = Scheduled.class, attribute = "timeUnit")
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    @AliasFor(annotation = Scheduled.class, attribute = "scheduler")
    String scheduler() default "";

    /**
     * 任务名（管理用），空则默认为 {@code 类名.方法名}。
     */
    String name() default "";

    /**
     * 任务描述（管理端展示用）。
     */
    String description() default "";
}
