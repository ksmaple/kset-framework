package com.kset.web;

import com.kset.web.config.KsetWebMvcConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KsetWebMvcConfigurerTest {

    @Test
    void registersDateAndDateTimeFormatters() {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();

        new KsetWebMvcConfigurer().addFormatters(conversionService);

        assertThat(conversionService.convert("2026-06-01", LocalDate.class))
                .isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(conversionService.convert("2026-06-01 10:20:30", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 20, 30));
    }
}
