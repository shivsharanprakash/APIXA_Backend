package com.apixa.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Step 17 forwarding bounds. A proxied request must never hang the gateway: a downstream service that
 * accepts a connection and then stalls, or never answers, has to fail with a controlled error instead of
 * occupying a gateway thread forever.
 *
 * <p>{@code connectTimeoutMs} bounds establishing the TCP connection (an unreachable service fails fast on
 * connect). {@code readTimeoutMs} bounds the wait for the downstream response, and is the value that
 * matters for long operations such as benchmark runs.
 */
@ConfigurationProperties(prefix = "apixa.gateway")
public record GatewayTimeoutProperties(
        @DefaultValue("2000") long connectTimeoutMs,
        @DefaultValue("120000") long readTimeoutMs) {}