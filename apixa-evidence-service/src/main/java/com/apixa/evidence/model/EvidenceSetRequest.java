package com.apixa.evidence.model;

import java.util.List;

/**
 * Step 12 evidence-set request: the already-computed Step 7 / Step 9 / Step 10 / Step 11 outputs for one
 * or more contract endpoints.
 *
 * <p>The evidence service performs <b>no analysis</b>: it never re-parses an OpenAPI document, never
 * scans Java source and never recomputes a conformance verdict. It only records facts and references.
 *
 * <p>Correlation reuses the identifiers APIXA already has — {@code analysisRunId} and
 * {@code contractId} — instead of inventing new ones. {@code evidenceSetId} is optional; when omitted
 * a deterministic one is derived from the contract identity (see {@code EvidenceService}).
 */
public record EvidenceSetRequest(
        String evidenceSetId,
        Long analysisRunId,
        String contractId,
        List<EndpointEvidence> endpoints) {

    /**
     * One chain: contract endpoint → mapping → source endpoint → source security rule → conformance.
     * Every section is optional: a {@code MULTIPLE_CANDIDATES} / {@code UNMATCHED} / unresolved case
     * still produces (and persists) evidence explaining why no verdict was established.
     */
    public record EndpointEvidence(
            ContractEvidence contract,
            MappingEvidence mapping,
            List<SourceEvidence> sourceEndpoints,
            SourceSecurityEvidence sourceSecurity,
            ConformanceEvidence conformance) {}

    /** Step 7 contract endpoint facts and references (no document body is stored). */
    public record ContractEvidence(String method, String path, String operationId, String jsonPath,
                                   String sourceFile, String securitySummary, String documentRef) {}

    /** Step 10 mapping facts, including the undecided cases. */
    public record MappingEvidence(String status, String confidence, String reason,
                                  String sourceMethod, String sourcePath,
                                  List<MappingCandidate> candidates) {}

    /** One Step 10 candidate, preserved verbatim — never collapsed. */
    public record MappingCandidate(String className, String methodName, String httpMethod, String path,
                                   String file, Integer lineStart) {}

    /** Step 8 source endpoint location. */
    public record SourceEvidence(String className, String methodName, String httpMethod, String path,
                                 String file, Integer lineStart, Integer lineEnd) {}

    /** Step 9 source security rule location. Never the source text of surrounding code. */
    public record SourceSecurityEvidence(String scope, String operator, String pathPattern,
                                         String expression, List<String> roles,
                                         List<String> authorities, String file,
                                         Integer lineStart, Integer lineEnd,
                                         Boolean unresolved, Boolean complex) {}

    /**
     * The Step 11 verdict as computed. Evidence records it verbatim and never re-derives it.
     */
    public record ConformanceEvidence(String status, String mappingStatus, String reason,
                                      String expectedSummary, String implementedSummary) {}
}