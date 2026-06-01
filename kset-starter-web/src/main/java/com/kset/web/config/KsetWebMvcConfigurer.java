package com.kset.web.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.format.DateTimeFormatter;

@Configuration
public class KsetWebMvcConfigurer implements WebMvcConfigurer {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer ksetJacksonCustomizer() {
        return builder -> {
            SimpleModule longModule = new SimpleModule();
            longModule.addSerializer(Long.class, ToStringSerializer.instance);
            longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.modulesToInstall(JavaTimeModule.class);
            builder.modulesToInstall(longModule);
            builder.simpleDateFormat("yyyy-MM-dd HH:mm:ss");
        };
    }

    @Bean
    public DateTimeFormatterRegistrar ksetDateTimeFormatterRegistrar() {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateFormatter(DateTimeFormatter.ISO_LOCAL_DATE);
        registrar.setDateTimeFormatter(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return registrar;
    }
}
