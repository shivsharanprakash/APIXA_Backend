package com.apixa.runtime.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Step 13 runtime verification request: exactly ONE HTTP request to describe.
 *
 * <p>The target is always supplied explicitly; the service never discovers targets, never reads a
 * local file through a URL and never calls another APIXA service.
 *
 * <p>{@code headers} may carry an {@code Authorization} value for local fixtures. It is used to perform
 * the request but is <b>never</b> echoed back, logged or persisted — see {@code RuntimeResult}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuntimeVerifyRequest(
        String targetBaseUrl,
        String path,
        String method,
        Map<String, String> headers,
        Map<String, String> queryParams,
        String body,
        Integer timeoutMs,
        Long analysisRunId,
        String contractId) {
}