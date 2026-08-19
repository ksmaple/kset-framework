package com.kset.redis.lock.annotation;

import com.kset.redis.lock.KsetRedisLockStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级 Redisson 分布式锁（Spring AOP）。
 * <p>
 * <b>注解不生效的常见场景</b>（与 Spring AOP 代理机制相同）：
 * <ul>
 *   <li>同类内部自调用：{@code this.sync()} 不会走代理，应改为注入自身接口、拆到另一 Bean，或使用 {@link com.kset.redis.lock.KsetRedisLockExecutor}</li>
 *   <li>方法非 {@code public}（含 {@code protected} / {@code private}）</li>
 *   <li>目标类未被 Spring 管理，或未开启 AOP 代理</li>
 *   <li>在 {@code static} 方法上使用</li>
 * </ul>
 * 启动时若开启 {@code kset.redis.lock.validate-targets=true}，会对上述可检测项输出 WARN。
 *
 * @see com.kset.redis.lock.KsetRedisLockExecutor
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KsetLocked {

    /**
     * 锁 key，支持 SpEL，例如 {@code "'order:sync'"} 或 {@code "'order:' + #orderId"}。
     * 与 {@link #keys()} 二选一；{@link #keys()} 非空时忽略本属性。
     */
    String value() default "";

    /**
     * 多锁 key（每项支持 SpEL），全部获取成功才进入方法体（Redisson MultiLock）。
     */
    String[] keys() default {};

    KsetRedisLockStrategy strategy() default KsetRedisLockStrategy.REJECT_IF_BUSY;

    /**
     * 等待时间，Spring Boot 时长格式（如 {@code 3s}、{@code 5m}）；空则按策略与全局 {@code lock-wait-time} 默认。
     */
    String waitTime() default "";

    /**
     * 租约时间；空则使用 {@code kset.redis.redisson.lock-lease-time}。
     * {@code 0s} 表示显式启用 Redisson watchdog 自动续期，适合超过默认 30 秒的临界区。
     * 不要把 {@code Duration.ZERO} 传给 {@code rejectNow} 或 {@code builder().leaseTime}，那会按 TTL 策略拒绝。
     */
    String lease() default "";
}
