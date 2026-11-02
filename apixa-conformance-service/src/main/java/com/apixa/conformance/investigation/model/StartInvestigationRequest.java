package com.apixa.conformance.investigation.model;

import com.apixa.common.model.SecurityPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StartInvestigationRequest(
        String projectId,
        String findingId,
        String method,
        String path,
        String contractId,
        String analysisId,
        String verdict,
        SecurityPolicy expectedSecurity,
        SecurityPolicy implementedSecurity,
        String implementationClass,
        String implementationMethod,
        String sourceFile,
        Integer sourceLine,
        String reason,
        String targetBaseUrl,
        boolean autoExecuteTest) {
}
