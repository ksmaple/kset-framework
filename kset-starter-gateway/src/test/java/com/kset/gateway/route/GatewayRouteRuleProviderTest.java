package com.kset.gateway.route;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayRouteRuleProviderTest {

    @Test
    void refreshesOnlyAfterRouteIsSaved() {
        RouteDefinitionRepository repository = mock(RouteDefinitionRepository.class);
        AtomicBoolean saved = new AtomicBoolean();
        AtomicInteger refreshes = new AtomicInteger();
        when(repository.save(any())).thenReturn(Mono.fromRunnable(() -> saved.set(true))
                .delaySubscription(Duration.ofMillis(20)).then());
        ApplicationEventPublisher publisher = new ApplicationEventPublisher() {
            @Override
            public void publishEvent(ApplicationEvent event) {
                assertThat(event).isInstanceOf(RefreshRoutesEvent.class);
                assertThat(saved.get()).isTrue();
                refreshes.incrementAndGet();
            }

            @Override
            public void publishEvent(Object event) {
                publishEvent((ApplicationEvent) event);
            }
        };

        new GatewayRouteRuleProvider(repository, publisher)
                .onRuleChanged("[{\"id\":\"orders\",\"uri\":\"lb://orders\"}]");

        assertThat(refreshes.get()).isEqualTo(1);
    }

    @Test
    void retriesDeletionAfterRepositoryFailure() {
        RouteDefinitionRepository repository = mock(RouteDefinitionRepository.class);
        AtomicInteger deletes = new AtomicInteger();
        when(repository.save(any())).thenReturn(Mono.empty());
        when(repository.delete(any())).thenAnswer(ignored -> Mono.defer(() -> {
            if (deletes.incrementAndGet() == 1) {
                return Mono.error(new IllegalStateException("temporary failure"));
            }
            return Mono.empty();
        }));
        AtomicInteger refreshes = new AtomicInteger();
        ApplicationEventPublisher publisher = new ApplicationEventPublisher() {
            @Override
            public void publishEvent(ApplicationEvent event) {
                refreshes.incrementAndGet();
            }

            @Override
            public void publishEvent(Object event) {
                publishEvent((ApplicationEvent) event);
            }
        };
        GatewayRouteRuleProvider provider = new GatewayRouteRuleProvider(repository, publisher);

        provider.onRuleChanged("[{\"id\":\"orders\",\"uri\":\"lb://orders\"}]");
        provider.onRuleChanged("");
        provider.onRuleChanged("");

        assertThat(deletes.get()).isEqualTo(2);
        assertThat(refreshes.get()).isEqualTo(2);
    }

    @Test
    void retriesSaveWithoutRefreshingFailedRoute() {
        RouteDefinitionRepository repository = mock(RouteDefinitionRepository.class);
        AtomicInteger saves = new AtomicInteger();
        when(repository.save(any())).thenAnswer(ignored -> Mono.defer(() -> {
            if (saves.incrementAndGet() == 1) {
                return Mono.error(new IllegalStateException("temporary failure"));
            }
            return Mono.empty();
        }));
        AtomicInteger refreshes = new AtomicInteger();
        ApplicationEventPublisher publisher = new ApplicationEventPublisher() {
            @Override
            public void publishEvent(ApplicationEvent event) {
                refreshes.incrementAndGet();
            }

            @Override
            public void publishEvent(Object event) {
                publishEvent((ApplicationEvent) event);
            }
        };
        GatewayRouteRuleProvider provider = new GatewayRouteRuleProvider(repository, publisher);
        String rules = "[{\"id\":\"orders\",\"uri\":\"lb://orders\"}]";

        provider.onRuleChanged(rules);
        assertThat(refreshes.get()).isZero();
        provider.onRuleChanged(rules);

        assertThat(saves.get()).isEqualTo(2);
        assertThat(refreshes.get()).isEqualTo(1);
    }
}
