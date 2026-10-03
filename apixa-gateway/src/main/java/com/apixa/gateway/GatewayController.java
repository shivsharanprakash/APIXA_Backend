package com.apixa.gateway;

import com.apixa.common.error.ErrorBody;
import com.apixa.common.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Step 17: the single frontend-facing entry point. The gateway performs <em>no</em> APIXA business logic --
 * it parses no OpenAPI, scans no source, maps nothing, computes no conformance, evidence, runtime result,
 * impact, benchmark or report. Every request is matched against an explicit prefix table and forwarded
 * byte-for-byte to the service that owns it.
 *
 * <p>Forwarding happens on raw bytes on purpose. Binding the body into {@code Object} (the previous
 * behaviour) forced Spring to deserialize and re-serialize it, which broke multipart uploads and any binary
 * response such as the Step 16 PDF. Reading the request stream and returning {@code byte[]} keeps
 * multipart/form-data, YAML text, JSON and PDF bytes exactly as the client sent them.
 */
@RestController
@RequestMapping("/api")
public class GatewayController {

    /**
     * Explicit route table: gateway prefix -> logical service name. The URL for each name comes from
     * {@link GatewayServicesProperties}, never from here. These prefixes are the class-level
     * {@code @RequestMapping} values of the real service controllers; each service owns its own sub-paths.
     * There is no catch-all route: an unmatched path is a 404 and is never forwarded anywhere.
     */
    private static final List<Map.Entry<String, String>> ROUTES = List.of(
            Map.entry("/api/projects", "project-service"),
            Map.entry("/api/contracts", "contract-service"),
            Map.entry("/api/source", "source-analysis-service"),
            Map.entry("/api/mappings", "mapping-service"),
            Map.entry("/api/conformance", "conformance-service"),
            Map.entry("/api/evidence", "evidence-service"),
            Map.entry("/api/runtime", "runtime-service"),
            Map.entry("/api/impact", "impact-service"),
            Map.entry("/api/benchmark", "benchmark-service"),
            Map.entry("/api/reports", "report-service"));

    /**
     * Hop-by-hop and framing headers (RFC 9110 7.6.1): Connection, Keep-Alive, Proxy-Authenticate,
     * Proxy-Authorization, TE, Trailer, Transfer-Encoding and Upgrade describe a single connection and must
     * not be copied. Host identifies the gateway and Content-Length describes its inbound framing; both are
     * recomputed by the HTTP client for the outbound request.
     */
    private static final Set<String> STRIPPED_REQUEST_HEADERS = Set.of(
            "host", "content-length", "connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade");

    /** Response headers worth returning verbatim; the report endpoints rely on content-disposition. */
    private static final List<String> FORWARDED_RESPONSE_HEADERS =
            List.of("content-disposition", "cache-control", "etag", "last-modified");

    private final RestTemplate restTemplate;
    private final GatewayServicesProperties services;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayController(GatewayServicesProperties services, GatewayTimeoutProperties timeouts) {
        this.services = services;
        // Bounded connect + read so a stalled downstream cannot hang a gateway request indefinitely.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeouts.connectTimeoutMs());
        factory.setReadTimeout((int) timeouts.readTimeoutMs());
        this.restTemplate = new RestTemplate(factory);
        // Downstream bodies are relayed verbatim (including non-2xx), so the default handler that throws on
        // 4xx/5xx stays off: this controller maps every status itself.
        this.restTemplate.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false; // every status, including 4xx/5xx, is relayed verbatim below
            }
        });
    }

    /** Gateway health. Local to the gateway: never proxied, and independent of downstream availability. */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "apixa-gateway", "timestamp", System.currentTimeMillis());
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String serviceName = route(uri);
        if (serviceName == null) {
            return error(404, "Not Found", "No gateway route for " + uri, uri);
        }
        String serviceUrl = services.url(serviceName);
        if (serviceUrl == null || serviceUrl.isBlank()) {
            return error(503, "Service Unavailable", "No configured URL for " + serviceName, uri);
        }
        // Path and query string pass through verbatim: the downstream service owns everything after the prefix.
        String target = serviceUrl + uri + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        byte[] body;
        try {
            body = readBody(request);
        } catch (IOException ex) {
            return error(400, "Bad Request", "Cannot read request body", uri);
        }
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(target,
                    HttpMethod.valueOf(request.getMethod()), new HttpEntity<>(body, forwardableHeaders(request)),
                    new ParameterizedTypeReference<byte[]>() {});
            return relay(response);
        } catch (ResourceAccessException ex) {
            // Connection refused, unreachable host or timeout: a transport failure, not a gateway fault.
            return error(502, "Bad Gateway", serviceName + " unreachable", uri);
        }
    }

    /** Longest matching prefix wins, so a future nested route can deliberately override a broader one. */
    private String route(String uri) {
        return ROUTES.stream()
                .filter(e -> pathMatcher.match(e.getKey() + "/**", uri) || pathMatcher.match(e.getKey(), uri))
                .max((a, b) -> Integer.compare(a.getKey().length(), b.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    /**
     * Copies the inbound headers minus hop-by-hop/framing ones. Authorization, cookies and API keys are
     * forwarded (the gateway is transparent and owns no auth of its own) but never logged: this class writes
     * no header value and no body content to the log.
     */
    private HttpHeaders forwardableHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (!STRIPPED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                headers.addAll(name, Collections.list(request.getHeaders(name)));
            }
        });
        headers.set(com.apixa.common.web.TraceIdFilter.HEADER, traceId(request));
        return headers;
    }

    /** Reuses an inbound trace id when the client sent one, otherwise mints one for the outbound call. */
    private String traceId(HttpServletRequest request) {
        String inbound = request.getHeader(com.apixa.common.web.TraceIdFilter.HEADER);
        if (inbound != null && !inbound.isBlank()) {
            TraceContext.set(inbound);
            return inbound;
        }
        return TraceContext.currentOrNew();
    }

    /** Raw bytes, so multipart/form-data and every other payload type survive untouched. */
    private byte[] readBody(HttpServletRequest request) throws IOException {
        if (request.getContentLength() == 0) {
            return new byte[0];
        }
        try (InputStream in = request.getInputStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * Status, bytes and content type go back unchanged -- 201 stays 201, a 404 ErrorBody stays a 404 ErrorBody,
     * and application/pdf stays PDF bytes. No gateway envelope is added around a downstream response.
     */
    private ResponseEntity<byte[]> relay(ResponseEntity<byte[]> response) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
        MediaType contentType = response.getHeaders().getContentType();
        builder.contentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM);
        FORWARDED_RESPONSE_HEADERS.stream()
                .filter(name -> response.getHeaders().getFirst(name) != null)
                .forEach(name -> builder.header(name, response.getHeaders().getFirst(name)));
        return builder.body(response.getBody() == null ? new byte[0] : response.getBody());
    }

    /**
     * Gateway-generated errors only (404 unknown route, 503 unconfigured service, 502 unreachable, 400 body).
     * The body is the shared {@link ErrorBody} shape, written directly so the gateway needs no JSON mapper
     * of its own; the values here are a status, a fixed reason, a message and a path, never request data.
     */
    private ResponseEntity<byte[]> error(int status, String reason, String message, String path) {
        String json = ("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\",\"timestamp\":%d}").formatted(
                status, reason, escape(message), escape(path), System.currentTimeMillis());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
