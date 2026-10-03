package com.apixa.report.render;

import com.apixa.report.model.ReportRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Step 16 sanitization applied before anything is rendered.
 *
 * <p>A report must never carry credentials. Runtime request/response headers are masked here, using the
 * same header classification the Step 13 runtime service already applies: a sensitive header is either
 * replaced with {@code [REDACTED]} or dropped entirely, so no token, cookie or API key can reach the
 * JSON, HTML or PDF rendering.
 *
 * <p>Only header maps are affected. No conformance status, mapping status, security policy, impact
 * category, evidence code or benchmark metric is modified anywhere in this step.
 */
@Component
public class ReportSanitizer {

    public static final String REDACTED = "[REDACTED]";

    /** Header names whose value is replaced with {@link #REDACTED}. */
    private static final Set<String> SENSITIVE = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie", "set-cookie2",
            "x-api-key", "api-key", "x-auth-token", "x-csrf-token", "x-amz-security-token",
            "x-client-secret", "x-secret");

    /** Header names that are omitted completely rather than masked. */
    private static final Set<String> DROPPED = Set.of("set-cookie", "set-cookie2");

    /** Returns a copy of the request with sanitized runtime headers; everything else is unchanged. */
    public ReportRequest sanitize(ReportRequest request) {
        if (request.runtime() == null || request.runtime().isEmpty()) return request;
        List<ReportRequest.RuntimeView> cleaned = request.runtime().stream().map(this::clean).toList();
        return new ReportRequest(request.project(), request.apiVersion(), request.analysisRun(),
                request.contract(), request.sourceAnalysis(), request.mappings(), request.conformance(),
                request.evidence(), cleaned, request.impact(), request.benchmark(), request.limitations());
    }

    private ReportRequest.RuntimeView clean(ReportRequest.RuntimeView v) {
        return new ReportRequest.RuntimeView(v.verificationId(), v.executionStatus(), v.method(), v.url(),
                v.observedStatusCode(), v.observedAt(), v.durationMs(), v.errorType(), v.errorMessage(),
                headers(v.requestHeaders(), true), headers(v.responseHeaders(), false));
    }

    /** Sorted output so header rendering is deterministic and never hash-ordered. */
    private Map<String, String> headers(Map<String, String> in, boolean mask) {
        if (in == null || in.isEmpty()) return null;
        Map<String, String> out = new LinkedHashMap<>();
        in.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> {
                    String name = e.getKey() == null ? "" : e.getKey();
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (DROPPED.contains(lower)) return;                       // omitted entirely
                    if (mask && SENSITIVE.contains(lower)) out.put(name, REDACTED);
                    else out.put(name, e.getValue());
                });
        return out.isEmpty() ? null : out;
    }
}