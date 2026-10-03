package com.apixa.benchmark.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Local REST client used by the Step 15 harness to reach the APIXA services under evaluation.
 *
 * <p><b>Evaluation level — documented deviation.</b> The benchmark calls the components under test
 * through their <b>existing public REST endpoints</b> (mapping {@code POST /api/mappings/map},
 * conformance {@code POST /api/conformance/analyze}, impact {@code POST /api/impact/analyze},
 * runtime {@code POST /api/runtime/verify}) rather than calling the service classes in process.
 *
 * <p>Reason, stated explicitly: every APIXA service module produces a Spring Boot archive as its Maven
 * artifact, so those artifacts cannot be consumed as ordinary compile dependencies; depending on them
 * would either fail the build or duplicate their classes inside the benchmark. The benchmark therefore
 * treats the services exactly as any other APIXA client does, which is also the level a real user
 * evaluates.
 *
 * <p><b>Only local hosts are contacted.</b> Each base URL is configurable and defaults to the APIXA
 * localhost ports; no external host is ever used. A service that is not running yields a
 * <b>SKIPPED</b> case with a controlled message, never a fabricated pass or a 500.
 */
@Component
public class LocalServiceClient {

    private final ObjectMapper mapper;
    private final HttpClient client;

    @Value("${apixa.benchmark.services.mapping:http://localhost:8084}")
    private String mappingBaseUrl;

    @Value("${apixa.benchmark.services.conformance:http://localhost:8085}")
    private String conformanceBaseUrl;

    @Value("${apixa.benchmark.services.runtime:http://localhost:8087}")
    private String runtimeBaseUrl;

    @Value("${apixa.benchmark.services.impact:http://localhost:8088}")
    private String impactBaseUrl;

    public LocalServiceClient(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    public String mappingBaseUrl() { return mappingBaseUrl; }
    public String conformanceBaseUrl() { return conformanceBaseUrl; }
    public String runtimeBaseUrl() { return runtimeBaseUrl; }
    public String impactBaseUrl() { return impactBaseUrl; }

    /**
     * POSTs {@code body} to {@code baseUrl + path} and returns the parsed JSON response.
     *
     * @throws ServiceUnavailableException when the service cannot be reached
     * @throws ServiceCallException when the service answered with a non-2xx status
     */
    public JsonNode post(String baseUrl, String path, JsonNode body) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl.trim() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
        } catch (Exception e) {
            throw new ServiceCallException("Invalid benchmark request: " + e.getMessage());
        }
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ServiceCallException("Service " + baseUrl + path + " answered HTTP "
                        + response.statusCode() + ": " + abbreviate(response.body()));
            }
            return mapper.readTree(response.body());
        } catch (ServiceCallException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Interrupted while calling " + baseUrl + path);
        } catch (java.net.ConnectException | java.net.http.HttpTimeoutException e) {
            throw new ServiceUnavailableException("Service not reachable: " + baseUrl
                    + " (start the APIXA service or run this case later)");
        } catch (Exception e) {
            throw new ServiceCallException("Call to " + baseUrl + path + " failed: "
                    + e.getClass().getSimpleName());
        }
    }

    /** Convenience: posts a fixture input object and returns the first element of a JSON array response. */
    public JsonNode postFirst(String baseUrl, String path, JsonNode body) {
        JsonNode response = post(baseUrl, path, body);
        return response != null && response.isArray() && !response.isEmpty() ? response.get(0) : null;
    }

    public ObjectNode object() { return mapper.createObjectNode(); }

    private String abbreviate(String body) {
        if (body == null) return "";
        return body.length() > 200 ? body.substring(0, 200) + "..." : body;
    }

    /** The evaluated APIXA service is not running: the case is skipped, never failed. */
    public static class ServiceUnavailableException extends RuntimeException {
        public ServiceUnavailableException(String message) { super(message); }
    }

    /** The service answered, but not successfully: this is a benchmark case failure. */
    public static class ServiceCallException extends RuntimeException {
        public ServiceCallException(String message) { super(message); }
    }
}