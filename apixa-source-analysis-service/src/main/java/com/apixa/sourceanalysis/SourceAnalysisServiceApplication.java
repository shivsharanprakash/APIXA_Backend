package com.apixa.sourceanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.apixa")
@ConfigurationPropertiesScan
public class SourceAnalysisServiceApplication {
    public static void main(String[] args) { SpringApplication.run(SourceAnalysisServiceApplication.class, args); }
}
