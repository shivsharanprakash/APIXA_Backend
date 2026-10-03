package com.apixa.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Step 16 wiring.
 *
 * <p>Only the JSON mapper is registered here. The report service starts no analysis engine and exposes
 * no other APIXA controller on port 8090.
 */
@Configuration
public class ReportConfiguration {

    /** Serializes the canonical report document to the JSON artifact. */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}