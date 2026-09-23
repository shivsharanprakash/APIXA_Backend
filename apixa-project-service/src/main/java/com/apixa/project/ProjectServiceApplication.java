package com.apixa.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.apixa")
@ConfigurationPropertiesScan
public class ProjectServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ProjectServiceApplication.class, args); }
}
