package com.apixa.conformance.investigation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvestigationSessionDto(
        String sessionId,
        String projectId,
        String findingId,
        String endpointMethod,
        String endpointPath,
        String status,                  // INITIALIZING, INVESTIGATING, AWAITING_APPROVAL, COMPLETED, FAILED
        String originalVerdict,
        List<InvestigationStepDto> steps,
        InvestigationDiagnosisDto diagnosis,
        ProposedTestDto proposedTest,
        RemediationDto remediation,
        String startedAt,
        String completedAt,
        String errorMessage) {
}
