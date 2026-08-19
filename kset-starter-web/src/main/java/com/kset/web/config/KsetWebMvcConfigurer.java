package com.kset.web.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.kset.common.utils.date.DateHelper;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.format.datetime.DateFormatterRegistrar;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class KsetWebMvcConfigurer implements WebMvcConfigurer {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(DateHelper.PATTERN_DEF);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer ksetJacksonCustomizer() {
        return builder -> {
            SimpleModule longModule = new SimpleModule();
            longModule.addSerializer(Long.class, ToStringSerializer.instance);
            longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME));
            javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME));
            javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DATE));
            javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE));
            builder.modulesToInstall(longModule, javaTimeModule);
            builder.simpleDateFormat(DateHelper.PATTERN_DEF);
        };
    }

    @Bean
    public DateFormatterRegistrar ksetDateFormatterRegistrar() {
        DateFormatterRegistrar registrar = new DateFormatterRegistrar();
        registrar.setFormatter(new DateFormatter(DateHelper.PATTERN_DEF));
        return registrar;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar timeRegistrar = new DateTimeFormatterRegistrar();
        timeRegistrar.setUseIsoFormat(false);
        timeRegistrar.setDateFormatter(DATE);
        timeRegistrar.setDateTimeFormatter(DATE_TIME);
        timeRegistrar.registerFormatters(registry);
        ksetDateFormatterRegistrar().registerFormatters(registry);
    }
}
