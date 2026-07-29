package com.kset.web.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.datetime.DateFormatter;
import org.springframework.format.datetime.DateFormatterRegistrar;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class KsetWebMvcConfigurer implements WebMvcConfigurer {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer ksetJacksonCustomizer() {
        return builder -> {
            SimpleModule longModule = new SimpleModule();
            longModule.addSerializer(Long.class, ToStringSerializer.instance);
            longModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.modulesToInstall(longModule);
            builder.simpleDateFormat("yyyy-MM-dd HH:mm:ss");
        };
    }

    @Bean
    public DateFormatterRegistrar ksetDateFormatterRegistrar() {
        DateFormatterRegistrar registrar = new DateFormatterRegistrar();
        registrar.setFormatter(new DateFormatter("yyyy-MM-dd HH:mm:ss"));
        return registrar;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        ksetDateFormatterRegistrar().registerFormatters(registry);
    }
}
