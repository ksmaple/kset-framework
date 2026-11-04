package com.kset.common.utils.thread;

import com.kset.common.exception.UncheckedInterruptedException;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
    public void clear() {
        try {
            super.clear();
        } finally {
            signalAllNotFull();
        }
    }

    @Override
    public int drainTo(Collection<? super E> target) {
        try {
            return super.drainTo(target);
        } finally {
            signalAllNotFull();
        }
    }

    @Override
    public int drainTo(Collection<? super E> target, int maxElements) {
        try {
            return super.drainTo(target, maxElements);
        } finally {
            signalAllNotFull();
        }
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        try {
            return super.removeIf(filter);
        } finally {
            signalAllNotFull();
        }
    }

    @Override
    public boolean removeAll(Collection<?> values) {
        try {
            return super.removeAll(values);
        } finally {
            signalAllNotFull();
        }
    }

    @Override
    public boolean retainAll(Collection<?> values) {
        try {
            return super.retainAll(values);
        } finally {
            signalAllNotFull();
        }
    }

    @Override
    public Iterator<E> iterator() {
        Iterator<E> iterator = super.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public E next() {
                return iterator.next();
            }

            @Override
            public void remove() {
                iterator.remove();
                signalNotFull(true);
            }
        };
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

    private void signalAllNotFull() {
        capacityLock.lock();
        try {
            notFull.signalAll();
        } finally {
            capacityLock.unlock();
        }
    }
}
