package com.apixa.benchmark;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.apixa")
@ConfigurationPropertiesScan
public class BenchmarkServiceApplication {
    public static void main(String[] args) { SpringApplication.run(BenchmarkServiceApplication.class, args); }
}
