package com.apixa.impact;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.apixa")
@ConfigurationPropertiesScan
public class ImpactServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ImpactServiceApplication.class, args); }
}
