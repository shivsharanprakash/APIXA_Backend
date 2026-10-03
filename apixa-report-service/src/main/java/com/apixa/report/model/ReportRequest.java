package com.apixa.report.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Step 16 report request: every result the report renders, supplied by the caller.
 *
 * <p>The report service is a <b>presentation layer</b>. It never imports an OpenAPI document, never
 * scans source, never maps endpoints, never computes conformance, never runs a verification, never
 * compares versions and never computes benchmark metrics. Every value below is already-produced output
 * that is rendered verbatim.
 *
 * <p>The nested records use the same wire field names as their producing services
 * ({@code ProjectDto}, {@code ApiVersionDto}, {@code AnalysisRunDto}, {@code ContractEndpointDto},
 * {@code SourceAnalysisResultDto}, {@code MappingResultDto}, {@code ConformanceResultDto},
 * {@code EvidenceSetResponse}, {@code RuntimeResult}, {@code ImpactResult}, {@code BenchmarkResult}), so
 * a payload produced by those services can be forwarded unchanged. They are transport shapes, not a
 * second domain model.
 *
 * <p>Every section is optional. A section that was not supplied is reported as {@code notProvided}
 * and never as an empty success or a false conclusion.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportRequest(
        ProjectSection project,
        ApiVersionSection apiVersion,
        AnalysisRunSection analysisRun,
        ContractSection contract,
        SourceAnalysisSection sourceAnalysis,
        List<MappingView> mappings,
        List<ConformanceView> conformance,
        EvidenceSection evidence,
        List<RuntimeView> runtime,
        ImpactSection impact,
        BenchmarkSection benchmark,
        List<String> limitations) {

    /** Supplied project identity (mirrors ProjectDto field names). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ProjectSection(Long id, String name, String description, String apiName,
                                 String baseUrl, String sourcePath, String sourceHash, String createdAt) {}

    /** Supplied API version identity (mirrors ApiVersionDto field names). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiVersionSection(Long id, Long projectId, String versionLabel, String openapiPath,
                                    String openapiHash, String notes, String createdAt) {}

    /** Supplied analysis run identity (mirrors AnalysisRunDto field names). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnalysisRunSection(Long id, Long projectId, Long apiVersionId, String status,
                                     String openapiHash, String sourceHash, String analyzerVersion,
                                     String resultSummary, String startedAt, String endedAt) {}

    /** Supplied Step 7 contract extraction (mirrors ContractExtractionResultDto field names). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContractSection(String contractId, String status, String sourceFile, String openapiVersion,
                                  String openapiHash, String title, String apiVersion,
                                  Integer endpointCount, Integer securitySchemeCount,
                                  List<ContractEndpointView> endpoints) {}

    /** One contract endpoint: exactly the Step 7 fields a report needs. No document body is stored. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContractEndpointView(String method, String path, String operationId, String jsonPath,
                                       String sourceFile, String summary, Boolean deprecated,
                                       SecurityView expectedSecurity) {}

    /** Supplied Step 8 / Step 9 source analysis (mirrors SourceAnalysisResultDto field names). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceAnalysisSection(String projectPath, String analysisType, String status,
                                        String analyzerVersion, Integer analyzedFiles, Integer endpointCount,
                                        List<SourceEndpointView> endpoints,
                                        List<SourceSecurityRuleView> securityRules,
                                        Integer securityRuleCount) {}

    /** One Step 8 source endpoint. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceEndpointView(String className, String methodName, String httpMethod, String path,
                                     String file, Integer lineStart, SecurityView implementedSecurity,
                                     String controllerPackage, Integer parameterCount) {}

    /** One Step 9 source security rule: location and rule metadata only, never source text. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceSecurityRuleView(String file, Integer lineStart, Integer lineEnd, String ruleType,
                                         String pathPattern, List<String> roles, List<String> authorities,
                                         List<String> scopes, String scope, String operator,
                                         String className, String methodName,
                                         Boolean complex, Boolean unresolved, Boolean catchAll) {}

    /** One Step 10 mapping result (mirrors MappingResultDto field names). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MappingView(String method, String path, String status, String confidence,
                              String implementationClass, String implementationMethod,
                              String implementationPath, Integer candidateCount, String reason,
                              List<MappingCandidateView> candidates) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MappingCandidateView(String className, String methodName, String httpMethod, String path,
                                       String file, Integer lineStart) {}

    /**
     * One Step 11 conformance result (mirrors ConformanceResultDto field names). The verdict, the
     * mapping status and the reason are rendered exactly as supplied.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConformanceView(String contractMethod, String contractPath, String operationId,
                                  String mappingStatus, String conformanceStatus,
                                  SecurityView expectedSecurity, SecurityView implementedSecurity,
                                  String sourceEndpointClass, String sourceEndpointMethod,
                                  String sourceEndpointPath, AppliedRuleView appliedRule,
                                  List<String> evidenceCodes, String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppliedRuleView(String scope, String operator, String pathPattern, Integer tier,
                                  String file, Integer lineStart) {}

    /** Supplied Step 12 evidence set (mirrors EvidenceSetResponse / EvidenceEntity field names). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvidenceSection(String evidenceSetId, Long analysisRunId, String contractId,
                                  Integer itemCount, List<EvidenceItemView> items) {}

    /** One evidence item: metadata and linkage only, never a complete source file. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EvidenceItemView(String evidenceCode, String sourceType, String evidenceSetCode,
                                   String httpMethod, String endpointPath, String file,
                                   Integer lineStart, Integer lineEnd, String jsonPath, String ruleType,
                                   String pathPattern, String description,
                                   List<String> relatedEvidenceCodes) {}

    /**
     * One Step 13 runtime observation (mirrors RuntimeResult field names). This is an observation, not a
     * verdict: the report never converts it into MATCH/MISMATCH/PARTIAL/UNVERIFIED. Request headers are
     * sanitized before they reach the report.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuntimeView(String verificationId, String executionStatus, String method, String url,
                              Integer observedStatusCode, String observedAt, Long durationMs,
                              String errorType, String errorMessage,
                              Map<String, String> requestHeaders,
                              Map<String, String> responseHeaders) {}

    /** Supplied Step 14 impact result (mirrors ImpactResult field names). Facts only, never ranked. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImpactSection(String v1VersionId, String v2VersionId, String status, Integer impactCount,
                                List<ImpactRecordView> impacts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImpactRecordView(String method, String path, String category, String v1Path, String v2Path,
                                   String v1Summary, String v2Summary, String v1ConformanceStatus,
                                   String v2ConformanceStatus, Integer v1ObservedStatusCode,
                                   Integer v2ObservedStatusCode, List<String> v1EvidenceCodes,
                                   List<String> v2EvidenceCodes, String details) {}

    /** Supplied Step 15 benchmark result (mirrors BenchmarkResult field names). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BenchmarkSection(String benchmarkId, String status, String interpretation,
                                   Integer totalCases, Integer executedCases, Integer skippedCases,
                                   Integer passedCases, Integer failedCases, Double accuracy,
                                   ConformanceBenchmarkView conformanceMetrics,
                                   MappingBenchmarkView mappingMetrics,
                                   ImpactBenchmarkView impactMetrics,
                                   RuntimeBenchmarkView runtimeMetrics,
                                   MutationBenchmarkView mutationMetrics,
                                   DeterminismView determinism,
                                   List<BenchmarkCaseView> cases) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConformanceBenchmarkView(Integer totalCases, Integer executedCases, Integer correctCases,
                                           Integer incorrectCases, Double accuracy, Double precision,
                                           Double recall, Double f1, String positiveClass,
                                           Map<String, Map<String, Integer>> confusionMatrix,
                                           Map<String, Integer> expectedCounts,
                                           Map<String, Integer> actualCounts,
                                           List<String> metricNotes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MappingBenchmarkView(Integer totalCases, Integer correctCases, Integer correctlyMatched,
                                       Integer correctlyUnmatched, Integer correctlyAmbiguous,
                                       Integer correctlyUncertain, List<String> metricNotes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImpactBenchmarkView(Integer changeCases, Integer correctlyDetected, Integer missedChanges,
                                      Integer falseChanges, Integer unchangedCases, Integer unchangedPreserved,
                                      List<String> metricNotes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuntimeBenchmarkView(Integer executedCases, Integer passedCases, Integer failedCases,
                                       Integer skippedCases, Integer requestExecutionSuccess,
                                       Integer observedStatusCorrect, Integer timeoutDetection,
                                       Integer connectionFailureDetection, LatencyView latency,
                                       List<String> metricNotes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LatencyView(Integer samples, Double minMs, Double maxMs, Double meanMs, Double medianMs) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MutationBenchmarkView(Integer totalCases, Integer correctCases, Integer incorrectCases,
                                        List<String> metricNotes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeterminismView(Integer repeats, Boolean deterministicContent, Boolean deterministicOrdering,
                                  List<String> varyingMetadataFields, String note) {}

    /** One benchmark case result, including any negative control such as CTRL-001. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BenchmarkCaseView(String caseId, String group, String description, String status,
                                    Map<String, Object> expected, Map<String, Object> actual,
                                    String discrepancy) {}

    /**
     * A normalized security policy exactly as Steps 7/9 produced it (mirrors SecurityPolicy field
     * names). It is rendered as data; the report never re-interprets it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SecurityView(String authentication, String schemeName, List<String> roles,
                               List<String> authorities, List<String> scopes,
                               Boolean permitAll, Boolean unknown) {}
}