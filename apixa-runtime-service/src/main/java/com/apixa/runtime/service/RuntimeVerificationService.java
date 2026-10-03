package com.apixa.runtime.service;

import com.apixa.common.error.ApiException;
import com.apixa.runtime.model.RuntimeResult;
import com.apixa.runtime.model.RuntimeResult.ErrorInfo;
import com.apixa.runtime.model.RuntimeResult.RequestInfo;
import com.apixa.runtime.model.RuntimeResult.ResponseInfo;
import com.apixa.runtime.model.RuntimeResult.RuntimeStatus;
import com.apixa.runtime.model.RuntimeVerifyRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Step 13 runtime verification: performs ONE active HTTP request against a running target API and
 * records what was observed.
 *
 * <p><b>Observation only.</b> Nothing here converts an HTTP status into a conformance verdict: an
 * observed {@code 401} or {@code 403} is reported as-is. The static Step 11 result is never modified and
 * the conformance service is never called.
 *
 * <p><b>Transport.</b> The JDK {@link HttpClient} is used, so no second HTTP client library is added.
 *
 * <p><b>Bounded by construction.</b> Every request has a connect and request timeout (default
 * {@value #DEFAULT_TIMEOUT_MS} ms, overridable per request within safe bounds), and at most
 * {@value #MAX_BODY_BYTES} body bytes are read.
 *
 * <p><b>Sanitization.</b> Credential-bearing request headers are masked to {@code [REDACTED]} in the
 * result, and sensitive response headers are omitted entirely. Nothing secret is logged or persisted —
 * this service keeps no database at all.
 */
@Service
public class RuntimeVerificationService {

    /** Conservative default so a verification can never hang indefinitely. */
    public static final int DEFAULT_TIMEOUT_MS = 5_000;
    public static final int MAX_TIMEOUT_MS = 60_000;
    /** Bounded response capture — 16 KB. */
    public static final int MAX_BODY_BYTES = 16 * 1024;
    public static final int MAX_REQUEST_BODY_BYTES = 16 * 1024;

    private static final Set<String> SUPPORTED_METHODS =
            Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    /** Request headers whose values must never be echoed back. */
    private static final Set<String> SENSITIVE_REQUEST_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "x-api-key", "api-key", "x-auth-token", "x-csrf-token", "x-amz-security-token");

    /** Response headers that are never returned. */
    private static final Set<String> SENSITIVE_RESPONSE_HEADERS = Set.of(
            "set-cookie", "set-cookie2", "authorization", "proxy-authorization",
            "x-api-key", "api-key", "x-auth-token");

    /** Response headers always kept when present (authentication testing needs these). */
    private static final List<String> SAFE_RESPONSE_HEADERS = List.of(
            "content-type", "content-length", "cache-control", "www-authenticate",
            "allow", "location", "retry-after", "strict-transport-security");

    private static final String REDACTED = "[REDACTED]";

    private final HttpClient client;

    public RuntimeVerificationService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(DEFAULT_TIMEOUT_MS))
                // Never follow redirects automatically: the observation must be the real first response.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
    public RuntimeResult verify(RuntimeVerifyRequest request) {
        if (request == null) throw ApiException.badRequest("Runtime verification request body is required");
        String method = normalizeMethod(request.method());
        URI uri = buildUri(request.targetBaseUrl(), request.path(), request.queryParams());
        int timeout = timeoutOf(request.timeoutMs());
        Map<String, String> sentHeaders = request.headers() == null ? Map.of() : request.headers();
        byte[] body = requestBody(request.body());

        RequestInfo requestInfo = new RequestInfo(method, uri.toString(), mask(sentHeaders), body.length);

        HttpRequest httpRequest;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeout));
            for (Map.Entry<String, String> h : sentHeaders.entrySet()) {
                if (h.getKey() != null && h.getValue() != null) builder.header(h.getKey(), h.getValue());
            }
            HttpRequest.BodyPublisher publisher = body.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body);
            builder.method(method, publisher);
            httpRequest = builder.build();
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid runtime request: " + ex.getMessage());
        }

        long start = System.nanoTime();
        try {
            HttpResponse<byte[]> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            // A response was received: the verification completed, whatever the status code is.
            return new RuntimeResult(UUID.randomUUID().toString(), RuntimeStatus.COMPLETED, requestInfo,
                    describe(response, durationMs), Instant.now().toString(), null,
                    request.analysisRunId(), request.contractId());
        } catch (java.net.http.HttpTimeoutException ex) {
            return failure(request, requestInfo, RuntimeStatus.TIMEOUT, "TIMEOUT",
                    "Target did not respond within " + timeout + " ms");
        } catch (java.net.ConnectException ex) {
            return failure(request, requestInfo, RuntimeStatus.FAILED, "CONNECTION_FAILURE",
                    "Could not connect to target: " + ex.getMessage());
        } catch (java.net.UnknownHostException ex) {
            return failure(request, requestInfo, RuntimeStatus.FAILED, "UNKNOWN_HOST",
                    "Unknown host: " + ex.getMessage());
        } catch (java.io.IOException ex) {
            return failure(request, requestInfo, RuntimeStatus.FAILED, "IO_FAILURE",
                    "Transport error: " + ex.getClass().getSimpleName());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure(request, requestInfo, RuntimeStatus.FAILED, "INTERRUPTED", "Verification interrupted");
        }
    }

    private RuntimeResult failure(RuntimeVerifyRequest request, RequestInfo info, RuntimeStatus status,
                                  String type, String message) {
        return new RuntimeResult(UUID.randomUUID().toString(), status, info, null, Instant.now().toString(),
                new ErrorInfo(type, message), request.analysisRunId(), request.contractId());
    }
    private ResponseInfo describe(HttpResponse<byte[]> response, long durationMs) {
        byte[] raw = response.body() == null ? new byte[0] : response.body();
        boolean truncated = raw.length > MAX_BODY_BYTES;
        byte[] captured = truncated ? java.util.Arrays.copyOf(raw, MAX_BODY_BYTES) : raw;

        String contentType = response.headers().firstValue("content-type").orElse(null);
        boolean textual = isTextual(contentType);
        String bodyText = null;
        String bodyNote = null;
        if (textual) {
            bodyText = maskSecrets(new String(captured, StandardCharsets.UTF_8));
            bodyNote = truncated ? "truncated at " + MAX_BODY_BYTES + " bytes" : null;
        } else {
            // Binary content: record type and size only, never the payload itself.
            bodyNote = "binary response body not captured (content-type " + contentType + ")";
        }

        Long declaredLength = response.headers().firstValue("content-length")
                .map(v -> { try { return Long.parseLong(v); } catch (NumberFormatException e) { return null; } })
                .orElse(null);

        return new ResponseInfo(response.statusCode(), contentType,
                declaredLength != null ? declaredLength : (long) raw.length,
                durationMs, safeHeaders(response), bodyText, captured.length, truncated, bodyNote);
    }

    /** Masks obvious credential patterns found inside an otherwise safe textual body. */
    private String maskSecrets(String text) {
        return text.replaceAll("(?i)(\"(?:password|passwd|token|access_token|refresh_token|api_key|secret|"
                + "client_secret|authorization)\"\\s*:\\s*\")[^\"]*(\")", "$1" + REDACTED + "$2");
    }

    private boolean isTextual(String contentType) {
        if (contentType == null) return false;   // unknown type: capture nothing rather than guess
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.contains("json") || ct.contains("text") || ct.contains("xml")
                || ct.contains("html") || ct.contains("javascript");
    }

    private Map<String, String> safeHeaders(HttpResponse<byte[]> response) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : SAFE_RESPONSE_HEADERS) {
            response.headers().firstValue(name).ifPresent(v -> out.put(name, v));
        }
        // Deterministic ordering for any additional non-sensitive header observed.
        response.headers().map().entrySet().stream()
                .filter(e -> !SENSITIVE_RESPONSE_HEADERS.contains(e.getKey().toLowerCase(Locale.ROOT)))
                .filter(e -> !out.containsKey(e.getKey().toLowerCase(Locale.ROOT)))
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.put(e.getKey().toLowerCase(Locale.ROOT), String.join(", ", e.getValue())));
        return out;
    }

    private Map<String, String> mask(Map<String, String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.nullsFirst(String::compareTo)))
                .forEach(e -> {
                    String key = e.getKey() == null ? "" : e.getKey();
                    boolean sensitive = SENSITIVE_REQUEST_HEADERS.contains(key.toLowerCase(Locale.ROOT));
                    out.put(key, sensitive ? REDACTED : e.getValue());
                });
        return out;
    }

    private byte[] requestBody(String body) {
        if (body == null || body.isEmpty()) return new byte[0];
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REQUEST_BODY_BYTES) {
            throw ApiException.badRequest("Request body exceeds the " + MAX_REQUEST_BODY_BYTES + " byte limit");
        }
        return bytes;
    }

    private int timeoutOf(Integer requested) {
        int timeout = requested == null ? DEFAULT_TIMEOUT_MS : requested;
        if (timeout <= 0) throw ApiException.badRequest("timeoutMs must be greater than 0");
        return Math.min(timeout, MAX_TIMEOUT_MS);
    }

    private String normalizeMethod(String method) {
        if (method == null || method.isBlank()) return "GET";
        String m = method.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_METHODS.contains(m)) throw ApiException.badRequest("Unsupported HTTP method: " + method);
        return m;
    }
    private URI buildUri(String baseUrl, String path, Map<String, String> queryParams) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw ApiException.badRequest("targetBaseUrl is required");
        }
        String base = baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String suffix = path == null ? "" : path.trim();
        if (!suffix.isEmpty() && !suffix.startsWith("/")) suffix = "/" + suffix;

        StringBuilder sb = new StringBuilder(base).append(suffix);
        if (queryParams != null && !queryParams.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : queryParams.entrySet()) {
                if (e.getKey() == null) continue;
                if (!first) sb.append('&');
                first = false;
                sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=')
                  .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
            }
        }
        URI uri;
        try {
            uri = URI.create(sb.toString());
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Invalid target URL: " + ex.getMessage());
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw ApiException.badRequest("Only http and https targets are supported");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw ApiException.badRequest("targetBaseUrl has no valid host");
        }
        return uri;
    }
}