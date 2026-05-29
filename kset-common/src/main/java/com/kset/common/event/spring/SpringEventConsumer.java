package com.kset.common.event.spring;

import com.kset.common.event.EventHandler;
import com.kset.common.monitor.facade.MonitorTransaction;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

public class SpringEventConsumer {

    private final List<EventHandler<?>> handlers;

    public SpringEventConsumer(List<EventHandler<?>> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    @EventListener
    public void onEvent(SpringEventWrapper wrapper) {
        dispatch(wrapper.getPayload());
    }

    @EventListener
    public void onOrderlyEvent(OrderlyEventWrapper wrapper) {
        dispatch(wrapper.getPayload());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransactionalEvent(TransactionalEventWrapper wrapper) {
        dispatch(wrapper.getPayload());
    }

    @SuppressWarnings("unchecked")
    private <T> void dispatch(T event) {
        if (event == null) {
            return;
        }
        for (EventHandler<?> handler : handlers) {
            if (handler.eventType().isAssignableFrom(event.getClass())) {
                MonitorTransaction transaction = EventMonitorSupport.beginConsume(event, handler);
                try {
                    ((EventHandler<T>) handler).handle(event);
                    EventMonitorSupport.success(transaction);
                } catch (RuntimeException | Error e) {
                    EventMonitorSupport.fail(transaction, e, "consume", event);
                    throw e;
                } finally {
                    EventMonitorSupport.close(transaction);
                }
            }
        }
    }

}
