package com.kset.cloud.dubbo.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dubbo 路由规则内存持有
 */
public final class DubboRouteRuleHolder {

    private static final CopyOnWriteArrayList<RouteCondition> CONDITIONS = new CopyOnWriteArrayList<>();
    private static volatile String metadataKey = "version";

    private DubboRouteRuleHolder() {
    }

    public static void setMetadataKey(String key) {
        if (key != null && !key.isBlank()) {
            metadataKey = key;
        }
    }

    public static String getMetadataKey() {
        return metadataKey;
    }

    public static void update(List<RouteCondition> conditions) {
        CONDITIONS.clear();
        if (conditions != null) {
            CONDITIONS.addAll(conditions);
        }
    }

    public static List<RouteCondition> getConditions() {
        return Collections.unmodifiableList(new ArrayList<>(CONDITIONS));
    }

    public static class RouteCondition {
        private String tag;
        private int weight = 100;

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }
    }

    public static class RouteRuleConfig {
        private List<RouteCondition> conditions = new ArrayList<>();

        public List<RouteCondition> getConditions() {
            return conditions;
        }

        public void setConditions(List<RouteCondition> conditions) {
            this.conditions = conditions;
        }
    }
}
