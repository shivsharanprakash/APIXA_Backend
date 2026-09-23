package com.apixa.gateway;

import com.apixa.common.error.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class GatewayController {

    private static final List<Map.Entry<String, String>> ROUTES = List.of(
            Map.entry("/api/projects/**", "project-service"),
            Map.entry("/api/contracts/**", "contract-service"),
            Map.entry("/api/source/**", "source-analysis-service"),
            Map.entry("/api/mappings/**", "mapping-service"),
            Map.entry("/api/conformance/**", "conformance-service"),
            Map.entry("/api/runtime/**", "runtime-service"),
            Map.entry("/api/evidence/**", "evidence-service"),
            Map.entry("/api/impact/**", "impact-service"),
            Map.entry("/api/reports/**", "report-service"),
            Map.entry("/api/benchmark/**", "benchmark-service"));

    private final RestTemplate restTemplate = new RestTemplate();
    private final GatewayServicesProperties services;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayController(GatewayServicesProperties services) { this.services = services; }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "apixa-gateway", "timestamp", System.currentTimeMillis());
    }

    @RequestMapping("/**")
    public ResponseEntity<Object> proxy(HttpServletRequest request, @RequestBody(required = false) Object body) {
        String uri = request.getRequestURI();
        String serviceName = ROUTES.stream().filter(e -> pathMatcher.match(e.getKey(), uri))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        if (serviceName == null) {
            return ResponseEntity.status(404).body(new ErrorBody(404, "Not Found", "No gateway route for " + uri, uri, System.currentTimeMillis()));
        }
        String serviceUrl = services.url(serviceName);
        if (serviceUrl == null) {
            return ResponseEntity.status(503).body(new ErrorBody(503, "Service Unavailable", "No configured URL for " + serviceName, uri, System.currentTimeMillis()));
        }
        String target = serviceUrl + uri + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(request.getContentType() != null ? MediaType.parseMediaType(request.getContentType()) : MediaType.APPLICATION_JSON);
        headers.setAccept(request.getHeader(HttpHeaders.ACCEPT) != null ? List.of(MediaType.parseMediaType(request.getHeader(HttpHeaders.ACCEPT))) : List.of(MediaType.ALL));
        headers.set(com.apixa.common.web.TraceIdFilter.HEADER, com.apixa.common.trace.TraceContext.currentOrNew());
        try {
            ResponseEntity<Object> response = restTemplate.exchange(target,
                    HttpMethod.valueOf(request.getMethod()), new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {});
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAs(Object.class));
        } catch (org.springframework.web.client.ResourceAccessException ex) {
            return ResponseEntity.status(502).body(new ErrorBody(502, "Bad Gateway", serviceName + " unreachable", uri, System.currentTimeMillis()));
        }
    }
}
