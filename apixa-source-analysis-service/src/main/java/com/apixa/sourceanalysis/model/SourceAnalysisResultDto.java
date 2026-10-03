package com.apixa.sourceanalysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Result of one source analysis pass over a local project directory.
 *
 * <p>{@code analysisType} distinguishes the three passes this service offers:
 * {@code FULL} = pre-existing pass (endpoints + Spring Security rules, Step 9 material),
 * {@code ENDPOINTS} = Step 8 source endpoint analysis (endpoint discovery only, so
 * {@code securityRules} is omitted and every endpoint is reported without security fields) and
 * {@code SECURITY} = Step 9 source security analysis (security rules only, so
 * {@code endpoints} is omitted).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceAnalysisResultDto(
        String projectPath,
        String analysisType,
        String status,
        String analyzerVersion,
        int analyzedFiles,
        int endpointCount,
        List<SourceEndpointDto> endpoints,
        List<SecurityEvidenceDto> securityRules,
        Integer securityRuleCount) {

    /** Backwards-compatible constructor for pre-Step-9 call sites (legacy FULL/ENDPOINTS passes). */
    public SourceAnalysisResultDto(String projectPath, String analysisType, String status,
            String analyzerVersion, int analyzedFiles, int endpointCount,
            List<SourceEndpointDto> endpoints, List<SecurityEvidenceDto> securityRules) {
        this(projectPath, analysisType, status, analyzerVersion, analyzedFiles, endpointCount,
                endpoints, securityRules, securityRules == null ? null : securityRules.size());
    }
}
