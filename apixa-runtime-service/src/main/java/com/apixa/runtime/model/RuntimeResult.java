package com.apixa.runtime.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Step 13 runtime verification result: what was <b>observed</b> at runtime.
 *
 * <p>This is deliberately NOT a conformance verdict. {@code status} describes the execution of the
 * verification itself (COMPLETED / FAILED / TIMEOUT) and {@code response.statusCode} is the raw HTTP
 * status returned by the target. An observed {@code 401} means "the target answered 401"; it is never
 * translated into MATCH / MISMATCH — that remains Step 11's job.
 *
 * <p>401 and 403 are preserved distinctly and are only described in terms of what HTTP proves:
 * 401 = the request was not authenticated; 403 = the request reached the application but access was
 * forbidden. No role is inferred from a status code.
 *
 * <p>{@code request.headers} is the <b>masked</b> header view; credential values are replaced by
 * {@code [REDACTED]} and never stored.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuntimeResult(
        String verificationId,
        RuntimeStatus status,
        RequestInfo request,
        ResponseInfo response,
        String observedAt,
        ErrorInfo error,
        Long analysisRunId,
        String contractId) {

    /** Execution status of the verification itself, never of the target's security posture. */
    public enum RuntimeStatus { COMPLETED, FAILED, TIMEOUT }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RequestInfo(String method, String url, Map<String, String> headers, Integer bodyBytes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseInfo(
            Integer statusCode,
            String contentType,
            Long contentLength,
            Long durationMs,
            Map<String, String> headers,
            String body,
            Integer bodyBytes,
            boolean bodyTruncated,
            String bodyNote) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorInfo(String type, String message) {}
}