package com.apixa.impact.model;

import com.apixa.common.model.SecurityPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Step 14 change-impact request: two already-computed APIXA snapshots to compare.
 *
 * <p>The service compares supplied results only. It never re-parses an OpenAPI document, never re-scans
 * source, never re-runs mapping or conformance and never performs active runtime testing. No other
 * APIXA service is called.
 *
 * <p>Correlation reuses the identifiers APIXA already has ({@code projectId}, {@code versionId},
 * {@code analysisRunId}, {@code contractId}); no unrelated ids are invented.
 *
 * <p>Every list is optional: only fields that are actually supplied are compared, so missing optional
 * metadata never manufactures a false change.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImpactRequest(VersionSnapshot v1, VersionSnapshot v2) {

    /** One analysed API version: its endpoints, source security rules and (optional) runtime observations. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VersionSnapshot(
            String projectId,
            String versionId,
            Long analysisRunId,
            String contractId,
            List<EndpointSnapshot> endpoints,
            List<SecurityRuleSnapshot> securityRules,
            List<RuntimeObservation> runtimeObservations) {}

    /**
     * One contract endpoint with its Step 10/11 outcome.
     *
     * <p>{@code SecurityPolicy} distinguishes an <b>explicitly absent</b> policy from a
     * <b>not provided</b> one: {@code null} means "no policy supplied for this version" (unknown, not
     * public), while a policy with {@code permitAll=true} / {@code authentication=NONE} explicitly means
     * public. An unknown is never collapsed into public.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EndpointSnapshot(
            String method,
            String path,
            String operationId,
            SecurityPolicy expectedSecurity,
            SecurityPolicy implementedSecurity,
            String mappingStatus,
            String conformanceStatus,
            List<String> evidenceCodes) {}

    /** One Step 9 source security rule; used to report matcher/operator changes. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SecurityRuleSnapshot(
            String scope,
            String operator,
            String pathPattern,
            SecurityPolicy policy,
            List<String> evidenceCodes) {}

    /**
     * One Step 13 runtime observation. Purely observational: a 401 is recorded as 401 and is never
     * turned into a conformance verdict or a security judgement here.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuntimeObservation(String method, String path, Integer observedStatusCode, String verificationId) {}
}