package com.kset.dubbo.route;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DubboRouteRuleProviderTest {

    private final DubboRouteRuleProvider provider = new DubboRouteRuleProvider();

    @AfterEach
    void resetRouteRules() {
        DubboRouteRuleHolder.configureLocalDefault("version", "stable");
    }

    @Test
    void blankNacosRuleFallsBackToLocalDefaultRule() {
        DubboRouteRuleHolder.configureLocalDefault("version", "stable");

        provider.onRuleChanged("");

        List<DubboRouteRuleHolder.RouteCondition> conditions = DubboRouteRuleHolder.getConditions();
        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0).getTag()).isEqualTo("stable");
        assertThat(conditions.get(0).getWeight()).isEqualTo(100);
    }

    @Test
    void nacosRuleOverridesLocalDefaultRule() {
        provider.onRuleChanged("""
                {
                  "conditions": [
                    { "tag": "v2", "weight": 10 },
                    { "tag": "stable", "weight": 90 }
                  ]
                }
                """);

        assertThat(DubboRouteRuleHolder.getConditions())
                .extracting(DubboRouteRuleHolder.RouteCondition::getTag)
                .containsExactly("v2", "stable");
    }

    @Test
    void invalidNacosRuleFallsBackToLocalDefaultRule() {
        provider.onRuleChanged("""
                {
                  "conditions": [
                    { "tag": "", "weight": 10 },
                    { "tag": "v2", "weight": 0 }
                  ]
                }
                """);

        List<DubboRouteRuleHolder.RouteCondition> conditions = DubboRouteRuleHolder.getConditions();
        assertThat(conditions).hasSize(1);
        assertThat(conditions.get(0).getTag()).isEqualTo("stable");
    }
}
