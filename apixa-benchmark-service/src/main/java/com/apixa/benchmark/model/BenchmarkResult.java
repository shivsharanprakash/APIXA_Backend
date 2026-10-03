package com.apixa.benchmark.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Step 15 benchmark result.
 *
 * <p>{@code status} describes the <b>execution of the benchmark itself</b> ({@code COMPLETED} /
 * {@code FAILED} / {@code PARTIALLY_EXECUTED}). A {@code FAILED} status means the harness could not
 * run; it never means a case mismatched. Case outcomes live in {@link CaseResult#status} and are
 * counted in {@code passedCases} / {@code failedCases}, which are reported separately and never
 * confused with the execution status.
 *
 * <p>Nothing here is a universal claim about APIXA: the result states how many controlled cases were
 * evaluated. Metrics that the fixture population cannot support are returned as {@code null} together
 * with an explanation, never as a manufactured number.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenchmarkResult(
        String benchmarkId,
        String status,
        String interpretation,
        String fixturePath,
        int totalCases,
        int executedCases,
        int skippedCases,
        int passedCases,
        int failedCases,
        /** passedCases / executedCases, or null when no case could be executed. */
        Double accuracy,
        Metrics metrics,
        Determinism determinism,
        DurationStats duration,
        List<CaseResult> cases) {

    /** Execution status of the benchmark run itself — distinct from case pass/fail. */
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String PARTIALLY_EXECUTED = "PARTIALLY_EXECUTED";

    public record CaseResult(
            String caseId,
            String group,
            String description,
            /** PASSED, FAILED, or SKIPPED when a required local target was unavailable. */
            String status,
            Map<String, Object> expected,
            Map<String, Object> actual,
            /** Concise discrepancy, or null when the case passed. Failures are never hidden. */
            String discrepancy,
            Long durationMs) {

        public static final String PASSED = "PASSED";
        public static final String FAILED = "FAILED";
        public static final String SKIPPED = "SKIPPED";
    }

    /** Per-group metrics. A group with no executed case reports nulls and explanatory notes, not zeros. */
    public record Metrics(
            ConformanceMetrics conformance,
            MappingMetrics mapping,
            ImpactMetrics impact,
            RuntimeMetrics runtime,
            MutationMetrics mutation) {}

    /**
     * Conformance classification metrics. {@code accuracy} is over executed CONFORMANCE cases only.
     * The confusion matrix holds raw counts of expected-vs-actual for the four Step 11 verdicts;
     * UNVERIFIED is a first-class row and is never silently folded into "incorrect" — the matrix shows
     * the real counts and {@code incorrectCases} is simply expected != actual.
     */
    public record ConformanceMetrics(
            int totalCases,
            int executedCases,
            int correctCases,
            int incorrectCases,
            Map<String, Integer> expectedCounts,
            Map<String, Integer> actualCounts,
            ConfusionMatrix confusionMatrix,
            Double accuracy,
            /**
             * Binary positive/negative metrics over {@code MISMATCH} as the positive class (a security
             * defect). Null with an explanation when the fixture contains no positive case or no
             * predicted positive, because precision/recall/F1 are undefined then.
             */
            Double precision,
            Double recall,
            Double f1,
            String positiveClass,
            List<String> metricNotes) {}

    /** expected verdict (row) x actual verdict (column), raw counts. */
    public record ConfusionMatrix(List<String> verdicts, Map<String, Map<String, Integer>> counts) {

        public int diagonal() {
            int n = 0;
            for (String v : verdicts) n += counts.getOrDefault(v, Map.of()).getOrDefault(v, 0);
            return n;
        }
    }

    /**
     * Step 10 mapping metrics over the real Step 10 vocabulary. MULTIPLE_CANDIDATES is reported as its
     * own outcome and is never merged into UNMATCHED.
     */
    public record MappingMetrics(
            int totalCases,
            int executedCases,
            int correctCases,
            int incorrectCases,
            int correctlyMatched,
            int correctlyUnmatched,
            int correctlyAmbiguous,
            int correctlyUncertain,
            Map<String, Integer> expectedCounts,
            Map<String, Integer> actualCounts,
            List<String> metricNotes) {}

    /** Step 14 impact metrics: detection only. No severity, no ranking of impact types. */
    public record ImpactMetrics(
            int totalCases,
            int executedCases,
            int changeCases,
            int correctlyDetected,
            int missedChanges,
            int falseChanges,
            int unchangedCases,
            int unchangedPreserved,
            Map<String, Integer> expectedCategoryCounts,
            Map<String, Integer> actualCategoryCounts,
            List<String> metricNotes) {}

    /** Step 13 runtime metrics against the local sample target. Raw aggregates, never rated. */
    public record RuntimeMetrics(
            int totalCases,
            int executedCases,
            int passedCases,
            int failedCases,
            int skippedCases,
            int requestExecutionSuccess,
            int observedStatusCorrect,
            int timeoutDetection,
            int connectionFailureDetection,
            DoubleLatency latency,
            List<String> metricNotes) {}

    public record DoubleLatency(Integer samples, Double minMs, Double maxMs, Double meanMs, Double medianMs) {}

    /** Mutation metrics: every mutation declares a required effect; only its detection is scored. */
    public record MutationMetrics(
            int totalCases,
            int executedCases,
            int correctCases,
            int incorrectCases,
            List<String> detectedEffects,
            List<String> metricNotes) {}

    /**
     * Determinism check. {@code deterministicContent} compares case outcomes and extracted actual
     * values across repeats; {@code varyingMetadataFields} names the intentionally generated fields
     * (ids, timestamps, durations) excluded from that comparison.
     */
    public record Determinism(
            Integer repeats,
            boolean deterministicContent,
            boolean deterministicOrdering,
            List<String> caseIdsCompared,
            List<String> varyingMetadataFields,
            String note) {}

    /** Wall-clock statistics of the benchmark execution itself. Descriptive only, never rated. */
    public record DurationStats(Integer samples, Long minMs, Long maxMs, Double meanMs, Double medianMs) {}
}