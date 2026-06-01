package com.kset.redis.lock.internal;

import com.kset.cloud.config.KsetRedisProperties;
import com.kset.redis.lock.KsetRedisLock;
import com.kset.redis.lock.KsetRedisLockException;
import com.kset.redis.core.KsetRedisTtlPolicy;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redisson 锁底层能力（单锁 / 多锁 / 阻塞获取）。
 */
public final class KsetRedissonLockProvider {

    private final RedissonClient redissonClient;
    private final KsetRedisTtlPolicy ttlPolicy;
    private final Duration defaultWaitTime;
    private final Duration defaultLeaseTime;

    public KsetRedissonLockProvider(RedissonClient redissonClient,
                                    KsetRedisProperties properties,
                                    KsetRedisTtlPolicy ttlPolicy) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient");
        this.ttlPolicy = Objects.requireNonNull(ttlPolicy, "ttlPolicy");
        KsetRedisProperties.Redisson redisson = properties.getRedisson();
        this.defaultWaitTime = redisson.getLockWaitTime();
        this.defaultLeaseTime = ttlPolicy.requireTtl(redisson.getLockLeaseTime());
    }

    public Optional<KsetRedisLock> tryLock(String lockKey, Duration waitTime, Duration leaseTime) {
        return tryLockAll(List.of(lockKey), waitTime, leaseTime).map(scope -> scope);
    }

    public Optional<KsetRedisLock> tryLockAll(Collection<String> lockKeys,
                                              Duration waitTime,
                                              Duration leaseTime) {
        List<String> keys = normalizeKeys(lockKeys);
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("lockKeys must not be empty");
        }
        Duration wait = waitTime != null ? waitTime : defaultWaitTime;
        Duration lease = ttlPolicy.requireTtl(leaseTime != null ? leaseTime : defaultLeaseTime);
        RLock rLock = toRedissonLock(keys);
        try {
            boolean acquired = rLock.tryLock(wait.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                return Optional.empty();
            }
            return Optional.of(wrapScope(keys, rLock));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    public KsetRedisLock lockBlocking(String lockKey, Duration leaseTime) {
        return lockBlockingAll(List.of(lockKey), leaseTime);
    }

    public KsetRedisLock lockBlockingAll(Collection<String> lockKeys, Duration leaseTime) {
        List<String> keys = normalizeKeys(lockKeys);
        Duration lease = ttlPolicy.requireTtl(leaseTime != null ? leaseTime : defaultLeaseTime);
        RLock rLock = toRedissonLock(keys);
        rLock.lock(lease.toMillis(), TimeUnit.MILLISECONDS);
        return wrapScope(keys, rLock);
    }

    public RLock fairLock(String lockKey) {
        return redissonClient.getFairLock(lockKey);
    }

    public RedissonClient redissonClient() {
        return redissonClient;
    }

    public Duration defaultWaitTime() {
        return defaultWaitTime;
    }

    public Duration defaultLeaseTime() {
        return defaultLeaseTime;
    }

    private RLock toRedissonLock(List<String> keys) {
        if (keys.size() == 1) {
            return redissonClient.getLock(keys.get(0));
        }
        RLock[] locks = keys.stream().map(redissonClient::getLock).toArray(RLock[]::new);
        return redissonClient.getMultiLock(locks);
    }

    private static KsetRedisLock wrapScope(List<String> keys, RLock rLock) {
        if (keys.size() == 1) {
            return new KsetRedissonLock(keys.get(0), rLock);
        }
        String composite = String.join("|", keys);
        return new KsetRedissonLockScope(composite, keys, rLock);
    }

    private static List<String> normalizeKeys(Collection<String> lockKeys) {
        if (lockKeys == null) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String key : lockKeys) {
            if (key != null && !key.isBlank()) {
                keys.add(key);
            }
        }
        return keys.stream().distinct().collect(Collectors.toList());
    }

    public String compositeKey(Collection<String> keys) {
        return String.join("|", normalizeKeys(keys));
    }

    static void ensureRedissonEnabled() {
        throw new KsetRedisLockException("",
                "Distributed lock requires Redisson; ensure Redisson is on the classpath and kset.redis.redisson.enabled is not false");
    }
}
