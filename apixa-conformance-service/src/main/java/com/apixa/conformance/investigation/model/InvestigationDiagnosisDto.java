package com.apixa.conformance.investigation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvestigationDiagnosisDto(
        String verdict,                  // MISMATCH, PARTIAL, WEAKER, STRONGER, UNVERIFIED
        String severity,                 // HIGH, MEDIUM, LOW, INFORMATIONAL
        String rootCauseAnalysis,
        String contractRequirementSummary,
        String implementationPolicySummary,
        String rulePrecedenceContext,
        List<String> evidenceCitations,
        List<String> remainingUncertainties,
        double confidenceScore) {
}
