package com.apixa.benchmark.engine;

import java.util.List;
import java.util.Map;

/**
 * One executed benchmark case: what was asked, what APIXA returned, and whether that matched the
 * fixture's ground truth. Produced by {@link BenchmarkHarness}; scored by {@link BenchmarkMetrics}.
 */
public record CaseOutcome(
        String caseId,
        String group,
        String description,
        String status,                 // PASSED | FAILED | SKIPPED
        Map<String, Object> expected,
        Map<String, Object> actual,
        String discrepancy,
        Long durationMs,
        /** Case-group specific observations used for metrics (confusion matrix, latency, ...). */
        Map<String, Object> observations) {

    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";

    public boolean passed() { return PASSED.equals(status); }
    public boolean skipped() { return SKIPPED.equals(status); }

    public Object observed(String key) { return observations == null ? null : observations.get(key); }
    public List<?> observedList(String key) {
        Object v = observed(key);
        return v instanceof List<?> l ? l : List.of();
    }
}