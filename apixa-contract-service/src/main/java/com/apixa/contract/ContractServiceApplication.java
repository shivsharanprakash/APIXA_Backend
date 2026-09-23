package com.apixa.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.apixa")
@ConfigurationPropertiesScan
public class ContractServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ContractServiceApplication.class, args); }
}
