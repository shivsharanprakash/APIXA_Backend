package com.apixa.conformance.model;

import com.apixa.common.model.SecurityPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Step 11 conformance result for ONE contract endpoint.
 *
 * <p>{@code mappingStatus} (the Step 10 endpoint correspondence verdict) is deliberately kept
 * separate from {@code conformanceStatus} (the Step 11 security comparison verdict): the two
 * questions are independent and must never be conflated. A missing implementation endpoint is an
 * {@code UNMATCHED} mapping and an {@code UNVERIFIED} conformance result — never a MISMATCH.
 *
 * <p>{@code conformanceStatus} is the pre-existing {@code SecurityConformanceEngine.Result} name
 * (MATCH / MISMATCH / PARTIAL / UNVERIFIED). No second conformance model is introduced.
 *
 * <p>{@code candidates} always preserves every source endpoint candidate the mapping reported, and
 * {@code appliedRule} records which Step 9 source security rule was selected (with its file/line),
 * so a decision is always traceable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConformanceResultDto(
        String contractMethod,
        String contractPath,
        String operationId,
        String mappingStatus,          // MATCHED, MULTIPLE_CANDIDATES, UNMATCHED, UNCERTAIN
        String conformanceStatus,      // MATCH, MISMATCH, PARTIAL, UNVERIFIED
        SecurityPolicy expectedSecurity,
        SecurityPolicy implementedSecurity,
        SourceEndpointView sourceEndpoint,
        AppliedRule appliedRule,
        List<SourceEndpointView> candidates,
        String reason) {

    /** The mapped source endpoint (Step 8 data), carried through unchanged. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceEndpointView(String className, String methodName, String httpMethod, String path,
                                     String file, Integer lineStart) {}

    /**
     * The Step 9 source security rule that was selected for this endpoint. {@code specificity} is the
     * precedence tier that won (see {@code SecurityConformanceEngine#selectRule}).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppliedRule(String scope, String operator, String pathPattern, Integer tier,
                              String file, Integer lineStart) {}
}