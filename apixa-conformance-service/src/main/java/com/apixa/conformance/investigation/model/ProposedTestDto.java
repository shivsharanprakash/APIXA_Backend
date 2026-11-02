package com.apixa.conformance.investigation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProposedTestDto(
        String testId,
        String endpointPath,
        String httpMethod,
        String testPrincipalRole,      // ANONYMOUS, USER, ADMIN, etc.
        String rationale,
        String targetUrl,
        Map<String, String> requestHeaders,
        String requestBody,
        int expectedStatusCode,
        Integer observedStatusCode,
        boolean requiresApproval,
        boolean approved,
        String executionStatus,         // PENDING_APPROVAL, EXECUTED, SKIPPED, FAILED
        String observedResponseSummary) {
}
