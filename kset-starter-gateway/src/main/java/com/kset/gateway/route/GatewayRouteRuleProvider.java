package com.kset.gateway.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.kset.cloud.spi.CloudRuleProvider;
import com.kset.cloud.spi.CloudRuleType;
import com.kset.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Slf4j
public class GatewayRouteRuleProvider implements CloudRuleProvider {

    private final RouteDefinitionRepository routeDefinitionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Set<String> managedRouteIds = new HashSet<>();

    public GatewayRouteRuleProvider(RouteDefinitionRepository routeDefinitionRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.routeDefinitionRepository = routeDefinitionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public CloudRuleType ruleType() {
        return CloudRuleType.GATEWAY_ROUTE;
    }

    @Override
    public synchronized void onRuleChanged(String jsonContent) {
        if (jsonContent == null || jsonContent.isBlank()) {
            removeManagedRoutes();
            return;
        }
        try {
            JsonNode root = JsonUtil.readTree(jsonContent);
            if (!root.isArray()) {
                log.warn("Gateway route config must be a JSON array");
                return;
            }
            List<RouteDefinition> definitions = new ArrayList<>();
            Set<String> newIds = new HashSet<>();
            for (JsonNode node : root) {
                RouteDefinition def = parseRoute(node);
                definitions.add(def);
                newIds.add(def.getId());
            }
            boolean changed = removeStaleRoutes(newIds);
            for (RouteDefinition definition : definitions) {
                if (saveRoute(definition)) {
                    managedRouteIds.add(definition.getId());
                    changed = true;
                }
            }
            refreshRoutes(changed);
            log.info("Gateway routes applied, managed={}", managedRouteIds.size());
        } catch (Exception e) {
            log.warn("Failed to refresh gateway routes: {}", e.getMessage());
        }
    }

    private boolean saveRoute(RouteDefinition definition) {
        try {
            routeDefinitionRepository.save(Mono.just(definition)).block();
            return true;
        } catch (RuntimeException error) {
            log.warn("Failed to save gateway route [{}]", definition.getId(), error);
            return false;
        }
    }

    private boolean deleteRoute(String routeId) {
        try {
            routeDefinitionRepository.delete(Mono.just(routeId)).block();
            managedRouteIds.remove(routeId);
            return true;
        } catch (RuntimeException error) {
            log.warn("Failed to delete gateway route [{}]", routeId, error);
            return false;
        }
    }

    private boolean removeStaleRoutes(Set<String> newIds) {
        Set<String> toRemove = new HashSet<>(managedRouteIds);
        toRemove.removeAll(newIds);
        boolean changed = false;
        for (String routeId : toRemove) {
            changed |= deleteRoute(routeId);
        }
        return changed;
    }

    private void removeManagedRoutes() {
        boolean changed = false;
        for (String routeId : new HashSet<>(managedRouteIds)) {
            changed |= deleteRoute(routeId);
        }
        refreshRoutes(changed);
    }

    private void refreshRoutes(boolean changed) {
        if (changed) {
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
        }
    }

    private RouteDefinition parseRoute(JsonNode node) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(node.get("id").asText());
        definition.setUri(URI.create(node.get("uri").asText()));

        if (node.has("predicates")) {
            List<org.springframework.cloud.gateway.handler.predicate.PredicateDefinition> predicates = new ArrayList<>();
            for (JsonNode predicateNode : node.get("predicates")) {
                org.springframework.cloud.gateway.handler.predicate.PredicateDefinition predicate =
                        new org.springframework.cloud.gateway.handler.predicate.PredicateDefinition();
                predicate.setName(predicateNode.get("name").asText());
                if (predicateNode.has("args")) {
                    Iterator<Map.Entry<String, JsonNode>> fields = predicateNode.get("args").fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        predicate.addArg(entry.getKey(), entry.getValue().asText());
                    }
                }
                predicates.add(predicate);
            }
            definition.setPredicates(predicates);
        }

        if (node.has("filters")) {
            List<org.springframework.cloud.gateway.filter.FilterDefinition> filters = new ArrayList<>();
            for (JsonNode filterNode : node.get("filters")) {
                org.springframework.cloud.gateway.filter.FilterDefinition filter =
                        new org.springframework.cloud.gateway.filter.FilterDefinition();
                filter.setName(filterNode.get("name").asText());
                if (filterNode.has("args")) {
                    Iterator<Map.Entry<String, JsonNode>> fields = filterNode.get("args").fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        filter.addArg(entry.getKey(), entry.getValue().asText());
                    }
                }
                filters.add(filter);
            }
            definition.setFilters(filters);
        }
        return definition;
    }
}
