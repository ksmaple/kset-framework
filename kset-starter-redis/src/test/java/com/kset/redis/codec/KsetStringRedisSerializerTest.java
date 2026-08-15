package com.kset.redis.codec;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class KsetStringRedisSerializerTest {

    private final KsetStringRedisSerializer serializer = new KsetStringRedisSerializer();

    @Test
    void serializesBasicValuesAsPlainText() {
        assertThat(new String(serializer.serialize("abc"), StandardCharsets.UTF_8)).isEqualTo("abc");
        assertThat(new String(serializer.serialize(12), StandardCharsets.UTF_8)).isEqualTo("12");
        assertThat(new String(serializer.serialize(true), StandardCharsets.UTF_8)).isEqualTo("true");
    }

    @Test
    void serializesObjectValuesAsPlainJsonString() {
        SampleUser user = new SampleUser();
        user.setId(1L);
        user.setName("Alice");

        byte[] bytes = serializer.serialize(user);
        String json = new String(bytes, StandardCharsets.UTF_8);

        assertThat(json).doesNotContain("@type", "@class");
        assertThat(json).contains("\"id\":1");
        assertThat(json).contains("\"name\":\"Alice\"");
        assertThat(serializer.deserialize(bytes)).isEqualTo(json);
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
    }
}
