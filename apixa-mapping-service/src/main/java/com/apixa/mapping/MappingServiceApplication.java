package com.apixa.mapping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.apixa")
@ConfigurationPropertiesScan
public class MappingServiceApplication {
    public static void main(String[] args) { SpringApplication.run(MappingServiceApplication.class, args); }
}
