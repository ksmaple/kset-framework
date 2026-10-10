package com.kset.agent.core.value;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentValueSnapshotTest {

    @Test
    void recursivelyCopiesStructuredValuesAndNormalizesArrays() {
        List<Object> nestedList = new ArrayList<>(List.of("first"));
        Set<Object> nestedSet = new LinkedHashSet<>(Set.of("alpha"));
        String[] nestedArray = {"one", "two"};
        Map<String, Object> nestedMap = new LinkedHashMap<>();
        nestedMap.put("list", nestedList);
        nestedMap.put("set", nestedSet);
        nestedMap.put("array", nestedArray);
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("nested", nestedMap);

        Map<String, Object> snapshot = AgentValueSnapshot.map(original);
        nestedList.add("second");
        nestedSet.add("beta");
        nestedArray[0] = "changed";
        nestedMap.put("late", "value");
        original.put("outside", "value");

        assertThat(snapshot).containsOnlyKeys("nested");
        Map<?, ?> copiedMap = (Map<?, ?>) snapshot.get("nested");
        assertThat(copiedMap.get("list")).isEqualTo(List.of("first"));
        assertThat(copiedMap.get("set")).isEqualTo(Set.of("alpha"));
        assertThat(copiedMap.get("array")).isEqualTo(List.of("one", "two"));
        assertThat(copiedMap.containsKey("late")).isFalse();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void returnsUnmodifiableContainersAtEveryStructuredLevel() {
        Map<String, Object> snapshot = AgentValueSnapshot.map(Map.of(
                "list", new ArrayList<>(List.of("value")),
                "set", new LinkedHashSet<>(Set.of("value")),
                "map", new LinkedHashMap<>(Map.of("key", "value")),
                "array", new String[]{"value"}));

        assertThatThrownBy(() -> snapshot.put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List) snapshot.get("list")).add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Set) snapshot.get("set")).add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map) snapshot.get("map")).put("other", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List) snapshot.get("array")).add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsCyclesInsteadOfLeakingMutableReferences() {
        Map<String, Object> cyclicMap = new LinkedHashMap<>();
        cyclicMap.put("self", cyclicMap);
        List<Object> cyclicList = new ArrayList<>();
        cyclicList.add(cyclicList);

        assertThatThrownBy(() -> AgentValueSnapshot.map(cyclicMap))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cyclic structured values");
        assertThatThrownBy(() -> AgentValueSnapshot.value(cyclicList))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cyclic structured values");
    }
}
