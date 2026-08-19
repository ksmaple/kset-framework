package com.kset.common.utils.date;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateHelperTest {

    @Test
    void parsesAndFormatsDefaultPattern() {
        DateHelper helper = DateHelper.parse("2024-06-07 14:05:30");

        assertThat(helper.toyyyyMMddHHmmss()).isEqualTo("2024-06-07 14:05:30");
        assertThat(helper.toyyyyMMdd()).isEqualTo("20240607");
        assertThat(helper.toyyyyMM()).isEqualTo("202406");
        assertThat(helper.toyyyy()).isEqualTo("2024");
    }

    @Test
    void parsesCompactAndDateOnly() {
        assertThat(DateHelper.parse("20240607").toyyyyMMddHHmmss()).isEqualTo("2024-06-07 00:00:00");
        assertThat(DateHelper.parse("2024-06-07").toyyyyMMddHHmmss()).isEqualTo("2024-06-07 00:00:00");
        assertThat(DateHelper.parse("202406").toyyyyMMddHHmmss()).isEqualTo("2024-06-01 00:00:00");
        assertThat(DateHelper.parse("2024").toyyyyMMddHHmmss()).isEqualTo("2024-01-01 00:00:00");
    }

    @Test
    void parsesMillisWithColon() {
        assertThat(DateHelper.parse("2024-06-07 14:05:30:123").toyyyyMMddHHmmssSSS())
                .isEqualTo("2024-06-07 14:05:30:123");
    }

    @Test
    void rejectsUnsupportedText() {
        assertThatThrownBy(() -> DateHelper.parse("not-a-date"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
