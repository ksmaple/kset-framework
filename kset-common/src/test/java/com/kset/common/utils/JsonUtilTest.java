package com.kset.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilTest {

    @Test
    void writesPlainJsonWithoutTypeMetadata() {
        SampleUser user = new SampleUser();
        user.setId(1L);
        user.setName("Alice");

        String json = JsonUtil.toJson(user);

        assertThat(json).doesNotContain("@type", "@class");
        assertThat(json).contains("\"id\":1");
        assertThat(json).contains("\"name\":\"Alice\"");
        assertThat(JsonUtil.fromJson(json, SampleUser.class)).isEqualTo(user);
    }

    @Test
    void convertsGenericJsonString() {
        String json = JsonUtil.toJson(List.of(Map.of("id", 1)));

        List<Map<String, Integer>> parsed = JsonUtil.fromJson(json, new TypeReference<>() {
        });

        assertThat(parsed).containsExactly(Map.of("id", 1));
    }

    @Test
    void copiesObjectToIndependentInstance() {
        SampleUser user = new SampleUser();
        user.setId(1L);
        user.setName("Alice");

        SampleUser copy = JsonUtil.copy(user, SampleUser.class);

        assertThat(copy).isEqualTo(user);
        assertThat(copy).isNotSameAs(user);
    }

    public static class SampleUser {
        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SampleUser that)) {
                return false;
            }
            return java.util.Objects.equals(id, that.id)
                    && java.util.Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, name);
        }
    }
}
