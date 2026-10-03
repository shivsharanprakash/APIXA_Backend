package com.apixa.impact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Step 14 change-impact result.
 *
 * <p>Every record is <b>factual</b>: it states what differs between the two supplied versions. There is
 * deliberately no severity, no ranking and no good/bad judgement — a change from MATCH to MISMATCH is
 * reported as a difference, not as a "critical" finding. Deciding what a change means is not this
 * step's job.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ImpactResult(
        String projectId,
        String v1VersionId,
        String v2VersionId,
        Long v1AnalysisRunId,
        Long v2AnalysisRunId,
        String v1ContractId,
        String v2ContractId,
        String status,
        int impactCount,
        List<ImpactRecord> impacts) {

    /** Status of the comparison itself: COMPLETED, or the reason it could not be completed. */
    public static final String COMPLETED = "COMPLETED";
    public static final String INSUFFICIENT_DATA = "INSUFFICIENT_DATA";

    /**
     * One detected change.
     *
     * <p>{@link #category} is one of the five factual categories. An endpoint can carry several records
     * when several independent changes exist, so a security change is never hidden behind a generic
     * "modified".
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImpactRecord(
            String method,
            String path,
            Category category,
            String v1Path,
            String v2Path,
            Side v1,
            Side v2,
            List<String> v1EvidenceCodes,
            List<String> v2EvidenceCodes,
            String details) {

        /** One side of a change, reduced to the tracked facts. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record Side(
                String operationId,
                String securitySummary,
                String implementedSecuritySummary,
                String mappingStatus,
                String conformanceStatus,
                Integer observedStatusCode) {}
    }

    /** Small, factual category set — no severity taxonomy. */
    public enum Category {
        /** present in V2, absent in V1 */
        ADDED_ENDPOINT,
        /** present in V1, absent in V2 */
        REMOVED_ENDPOINT,
        /** same logical endpoint, but a tracked non-security attribute changed */
        MODIFIED_ENDPOINT,
        /** expected or implemented security (or a source security rule) changed, both sides known */
        SECURITY_CHANGED,
        /**
         * security information differs because one side is UNKNOWN or NOT_PROVIDED — a data
         * availability / completeness difference between the two supplied versions, <b>not</b> a proven
         * policy change. Never a safety judgement and never a reason to treat UNKNOWN as PUBLIC.
         */
        SECURITY_INFORMATION_CHANGED,
        /** Step 11 conformance status changed */
        CONFORMANCE_CHANGED,
        /** Step 13 runtime observation changed — observationally only */
        RUNTIME_OBSERVATION_CHANGED
    }
}