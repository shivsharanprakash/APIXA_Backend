package com.apixa.benchmark.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Step 15 wiring.
 *
 * <p>Only the JSON mapper is registered here. The evaluated components are reached through their own
 * public REST endpoints on localhost (see {@code LocalServiceClient}), so this service starts no engine
 * and exposes no Step 6-14 controller on port 8089.
 */
@Configuration
public class BenchmarkConfiguration {

    /** Reads the ground-truth fixture and shapes local service requests/responses. */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}