package com.kset.gateway.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.kset.cloud.spi.CloudRuleProvider;
import com.kset.cloud.spi.CloudRuleType;
import com.kset.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
public class GatewayRouteRuleProvider implements CloudRuleProvider {

    private final RouteDefinitionWriter routeDefinitionWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Set<String> managedRouteIds = ConcurrentHashMap.newKeySet();

    public GatewayRouteRuleProvider(RouteDefinitionWriter routeDefinitionWriter,
                                    ApplicationEventPublisher eventPublisher) {
        this.routeDefinitionWriter = routeDefinitionWriter;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public CloudRuleType ruleType() {
        return CloudRuleType.GATEWAY_ROUTE;
    }

    @Override
    public void onRuleChanged(String jsonContent) {
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
            removeStaleRoutes(newIds);
            definitions.forEach(def -> routeDefinitionWriter.save(Mono.just(def)).subscribe());
            managedRouteIds.clear();
            managedRouteIds.addAll(newIds);
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("Gateway routes refreshed, count={}", definitions.size());
        } catch (Exception e) {
            log.warn("Failed to refresh gateway routes: {}", e.getMessage());
        }
    }

    private void removeStaleRoutes(Set<String> newIds) {
        Set<String> toRemove = new HashSet<>(managedRouteIds);
        toRemove.removeAll(newIds);
        for (String routeId : toRemove) {
            routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
        }
    }

    private void removeManagedRoutes() {
        for (String routeId : new HashSet<>(managedRouteIds)) {
            routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
        }
        managedRouteIds.clear();
        eventPublisher.publishEvent(new RefreshRoutesEvent(this));
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
