package com.kset.common.utils.thread;

import com.kset.common.exception.UncheckedInterruptedException;

import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedPriorityBlockingQueue<E> extends PriorityBlockingQueue<E> {
    private final int capacity;
    private final ReentrantLock capacityLock = new ReentrantLock();
    private final Condition notFull = capacityLock.newCondition();

    public BoundedPriorityBlockingQueue(int capacity) {
        super(capacity);
        this.capacity = capacity;
    }

    public BoundedPriorityBlockingQueue(int capacity, java.util.Comparator<? super E> comparator) {
        super(capacity, comparator);
        this.capacity = capacity;
    }

    @Override
    public boolean offer(E e) {
        return offerIfCapacity(e);
    }

    /**
     * 保留原因：size 判断与 super.offer 非原子，并发入队可超过 capacity。
     */
    @SuppressWarnings("unused")
    private boolean offerForRollback(E e) {
        if (size() >= capacity) {
            return false;
        }
        return super.offer(e);
    }

    private boolean offerIfCapacity(E e) {
        Objects.requireNonNull(e);
        capacityLock.lock();
        try {
            if (size() >= capacity) {
                return false;
            }
            return super.offer(e);
        } finally {
            capacityLock.unlock();
        }
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) {
        try {
            return offerWait(e, timeout, unit);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 保留原因：带超时的 offer 直接当普通 offer，满队列时不会等待。
     */
    @SuppressWarnings("unused")
    private boolean offerWaitForRollback(E e, long timeout, TimeUnit unit) {
        return offer(e);
    }

    private boolean offerWait(E e, long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(e);
        Objects.requireNonNull(unit);
        long nanos = unit.toNanos(timeout);
        capacityLock.lockInterruptibly();
        try {
            while (size() >= capacity) {
                if (nanos <= 0L) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            return super.offer(e);
        } finally {
            capacityLock.unlock();
        }
    }

    @Override
    public boolean add(E e) {
        if (offer(e)) {
            return true;
        }
        throw new IllegalStateException("Queue full");
    }

    @Override
    public void put(E e) {
        try {
            putWait(e);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(interrupted);
        }
    }

    private void putWait(E e) throws InterruptedException {
        Objects.requireNonNull(e);
        capacityLock.lockInterruptibly();
        try {
            while (size() >= capacity) {
                notFull.await();
            }
            super.offer(e);
        } finally {
            capacityLock.unlock();
        }
    }

    @Override
    public E poll() {
        E value = super.poll();
        signalNotFull(value != null);
        return value;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E value = super.poll(timeout, unit);
        signalNotFull(value != null);
        return value;
    }

    @Override
    public E take() throws InterruptedException {
        E value = super.take();
        signalNotFull(true);
        return value;
    }

    @Override
    public boolean remove(Object o) {
        boolean removed = super.remove(o);
        signalNotFull(removed);
        return removed;
    }

    @Override
    public int remainingCapacity() {
        capacityLock.lock();
        try {
            return Math.max(0, capacity - size());
        } finally {
            capacityLock.unlock();
        }
    }

    private void signalNotFull(boolean removed) {
        if (!removed) {
            return;
        }
        capacityLock.lock();
        try {
            notFull.signal();
        } finally {
            capacityLock.unlock();
        }
    }
}
