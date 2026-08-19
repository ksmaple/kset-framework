package com.kset.redis.lock;

/**
 * Thrown when waiting for a lock is interrupted.
 */
public class KsetRedisLockInterruptedException extends KsetRedisLockException {

    public KsetRedisLockInterruptedException(String lockKey, InterruptedException cause) {
        super(lockKey, "Lock wait interrupted: " + lockKey, cause);
    }
}
