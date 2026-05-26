package com.kset.demo.standalone;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.kset.demo.standalone.mapper")
public class DemoStandaloneApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoStandaloneApplication.class, args);
    }
}
