package com.kset.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionUtilTest {

    @Test
    void comparesCommonNumberVersions() {
        assertThat(VersionUtil.isEqual("1.0", "1.0.0.0")).isTrue();
        assertThat(VersionUtil.greaterThan("1.0.1", "1.0.0.9")).isTrue();
        assertThat(VersionUtil.lessThan("2.9.9", "10.0.0")).isTrue();
        assertThat(VersionUtil.greaterThan("2025.0.0.0", "2024.9.9")).isTrue();
    }

    @Test
    void supportsPrefixSuffixAndBuildMetadata() {
        assertThat(VersionUtil.isEqual("v1.2.0", "1.2")).isTrue();
        assertThat(VersionUtil.lessThan("1.0.0-SNAPSHOT", "1.0.0")).isTrue();
        assertThat(VersionUtil.lessThan("1.0.0-beta", "1.0.0-rc1")).isTrue();
        assertThat(VersionUtil.lessThan("1.0.0-rc1", "1.0.0-rc2")).isTrue();
        assertThat(VersionUtil.isEqual("1.0.0.Final", "1.0.0")).isTrue();
        assertThat(VersionUtil.isEqual("1.0.0+build1", "1.0.0+build2")).isTrue();
    }

    @Test
    void checksAtLeastAndRange() {
        assertThat(VersionUtil.isAtLeast("1.5.0", "1.4.9")).isTrue();
        assertThat(VersionUtil.inRange("1.5.0", "1.0.0", "2.0.0")).isTrue();
        assertThat(VersionUtil.inRange("2.0.0", "1.0.0", "2.0.0")).isFalse();
    }

    @Test
    void validatesVersionText() {
        assertThat(VersionUtil.isValid("1.0.0.0")).isTrue();
        assertThat(VersionUtil.isValid("")).isFalse();
        assertThat(VersionUtil.isValid("1..0")).isFalse();
        assertThat(VersionUtil.isValid("1.0.0.")).isFalse();
        assertThat(VersionUtil.isValid("1.0.0-")).isFalse();

        assertThatThrownBy(() -> VersionUtil.compare(null, "1.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
