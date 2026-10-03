package com.apixa.benchmark.engine;

import com.apixa.benchmark.model.BenchmarkResult;
import com.apixa.benchmark.model.BenchmarkResult.ConfusionMatrix;
import com.apixa.benchmark.model.BenchmarkResult.ConformanceMetrics;
import com.apixa.benchmark.model.BenchmarkResult.DoubleLatency;
import com.apixa.benchmark.model.BenchmarkResult.ImpactMetrics;
import com.apixa.benchmark.model.BenchmarkResult.MappingMetrics;
import com.apixa.benchmark.model.BenchmarkResult.MutationMetrics;
import com.apixa.benchmark.model.BenchmarkResult.RuntimeMetrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Step 15 metric computation over executed case outcomes.
 *
 * <p><b>Honesty rules enforced here.</b>
 * <ul>
 *   <li>A metric whose denominator does not exist is {@code null} plus an explanatory note. No
 *       denominator is ever invented and no metric is defaulted to 0 or 1.</li>
 *   <li>UNVERIFIED is a real Step 11 verdict: it gets its own row in the confusion matrix and is only
 *       "incorrect" when the fixture expected something else.</li>
 *   <li>Step 10 vocabulary is preserved: MULTIPLE_CANDIDATES is never counted as UNMATCHED.</li>
 *   <li>Step 14 contributes detection counts only — no severity, no ranking of impact categories.</li>
 *   <li>Latency is reported as raw min/max/mean/median. It is never labelled good or bad.</li>
 * </ul>
 */
public final class BenchmarkMetrics {

    /** The four Step 11 verdicts, in the order used by the confusion matrix. */
    public static final List<String> VERDICTS = List.of("MATCH", "MISMATCH", "PARTIAL", "UNVERIFIED");

    /** Positive class for the binary metrics: a security defect. */
    public static final String POSITIVE = "MISMATCH";

    private BenchmarkMetrics() {}

    /**
     * Accuracy is correct/executed. Precision/recall/F1 treat MISMATCH as the positive class
     * (a detected security defect) and every other verdict as negative. They are reported only when the
     * fixture actually supports them: at least one expected MISMATCH and at least one predicted one.
     */
    public static ConformanceMetrics conformance(List<CaseOutcome> cases) {
        List<CaseOutcome> executed = cases.stream().filter(c -> !c.skipped()).toList();
        Map<String, Integer> expectedCounts = new TreeMap<>();
        Map<String, Integer> actualCounts = new TreeMap<>();
        Map<String, Map<String, Integer>> matrix = new LinkedHashMap<>();
        for (String v : VERDICTS) {
            matrix.put(v, new TreeMap<>());
            VERDICTS.forEach(a -> matrix.get(v).put(a, 0));
        }
        int correct = 0;
        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (CaseOutcome c : executed) {
            String e = str(c.expected(), "conformanceStatus");
            String a = str(c.actual(), "conformanceStatus");
            if (e == null || a == null) continue;
            expectedCounts.merge(e, 1, Integer::sum);
            actualCounts.merge(a, 1, Integer::sum);
            if (matrix.containsKey(e) && matrix.get(e).containsKey(a)) matrix.get(e).merge(a, 1, Integer::sum);
            if (e.equals(a)) correct++;
            if (POSITIVE.equals(e) && POSITIVE.equals(a)) tp++;
            else if (!POSITIVE.equals(e) && POSITIVE.equals(a)) fp++;
            else if (POSITIVE.equals(e)) fn++;
            else tn++;
        }
        List<String> notes = new ArrayList<>();
        Double precision = null, recall = null, f1 = null;
        if (tp + fp == 0) {
            notes.add("precision is undefined: no case was predicted " + POSITIVE);
        } else {
            precision = (double) tp / (tp + fp);
        }
        if (tp + fn == 0) {
            notes.add("recall is undefined: the fixture contains no expected " + POSITIVE + " case");
        } else {
            recall = (double) tp / (tp + fn);
        }
        if (precision == null || recall == null || (precision + recall) == 0) {
            notes.add("f1 is undefined without both precision and recall");
        } else {
            f1 = 2 * precision * recall / (precision + recall);
        }
        if (executed.isEmpty()) notes.add("no conformance case was executed");
        notes.add("binary metrics use positive class " + POSITIVE
                + "; UNVERIFIED counts as negative there but keeps its own confusion-matrix row");
        return new ConformanceMetrics(cases.size(), executed.size(), correct, executed.size() - correct,
                expectedCounts, actualCounts, new ConfusionMatrix(VERDICTS, matrix),
                executed.isEmpty() ? null : (double) correct / executed.size(),
                precision, recall, f1, POSITIVE, notes);
    }
    /** Step 10 metrics over the real mapping vocabulary; MULTIPLE_CANDIDATES stays its own outcome. */
    public static MappingMetrics mapping(List<CaseOutcome> cases) {
        List<CaseOutcome> executed = cases.stream().filter(c -> !c.skipped()).toList();
        Map<String, Integer> expectedCounts = new TreeMap<>();
        Map<String, Integer> actualCounts = new TreeMap<>();
        int correct = 0, matched = 0, unmatched = 0, ambiguous = 0, uncertain = 0;
        for (CaseOutcome c : executed) {
            String e = str(c.expected(), "status");
            String a = str(c.actual(), "status");
            if (e == null || a == null) continue;
            expectedCounts.merge(e, 1, Integer::sum);
            actualCounts.merge(a, 1, Integer::sum);
            if (e.equals(a)) {
                correct++;
                switch (e) {
                    case "MATCHED" -> matched++;
                    case "UNMATCHED" -> unmatched++;
                    case "MULTIPLE_CANDIDATES" -> ambiguous++;
                    case "UNCERTAIN" -> uncertain++;
                    default -> { }
                }
            }
        }
        List<String> notes = new ArrayList<>();
        if (executed.isEmpty()) notes.add("no mapping case was executed");
        notes.add("MULTIPLE_CANDIDATES (ambiguous) is counted separately and never as UNMATCHED");
        return new MappingMetrics(cases.size(), executed.size(), correct, executed.size() - correct,
                matched, unmatched, ambiguous, uncertain, expectedCounts, actualCounts, notes);
    }

    /**
     * Step 14 metrics: a "change case" expects at least one impact record, an "unchanged case" expects
     * none. Detection counts only — categories are never ranked or weighted.
     */
    public static ImpactMetrics impact(List<CaseOutcome> cases) {
        List<CaseOutcome> executed = cases.stream().filter(c -> !c.skipped()).toList();
        Map<String, Integer> expectedCategories = new TreeMap<>();
        Map<String, Integer> actualCategories = new TreeMap<>();
        int changeCases = 0, detected = 0, missed = 0, falsePositives = 0, unchanged = 0, preserved = 0;
        for (CaseOutcome c : executed) {
            String expectedCount = str(c.expected(), "impactCount");
            String actualCount = str(c.actual(), "impactCount");
            if (expectedCount == null || actualCount == null) continue;
            int e = Integer.parseInt(expectedCount);
            int a = Integer.parseInt(actualCount);
            if (e > 0) {
                changeCases++;
                if (a > 0) detected++;
                else missed++;
            } else {
                unchanged++;
                if (a == 0) preserved++;
                else falsePositives++;
            }
            categories(c.expected(), "categories").forEach(k -> expectedCategories.merge(k, 1, Integer::sum));
            categories(c.actual(), "categories").forEach(k -> actualCategories.merge(k, 1, Integer::sum));
        }
        List<String> notes = new ArrayList<>();
        if (executed.isEmpty()) notes.add("no impact case was executed");
        notes.add("detection only: Step 14 defines no severity, so no impact category is weighted or ranked");
        return new ImpactMetrics(cases.size(), executed.size(), changeCases, detected, missed,
                falsePositives, unchanged, preserved, expectedCategories, actualCategories, notes);
    }
    /**
     * Step 13 metrics: execution success, observed-status correctness against the fixture's explicit
     * expected status, timeout detection and connection-failure detection. Latency is a raw aggregate.
     */
    public static RuntimeMetrics runtime(List<CaseOutcome> cases) {
        List<CaseOutcome> executed = cases.stream().filter(c -> !c.skipped()).toList();
        int passed = 0, failed = 0, skipped = (int) cases.stream().filter(CaseOutcome::skipped).count();
        int execSuccess = 0, statusCorrect = 0, timeouts = 0, connFailures = 0;
        List<Double> latencies = new ArrayList<>();
        for (CaseOutcome c : executed) {
            if (c.passed()) passed++;
            else failed++;
            String status = str(c.observations(), "runtimeStatus");
            String expectedStatus = str(c.expected(), "status");
            if (expectedStatus != null && expectedStatus.equals(status)) execSuccess++;
            String expectedCode = str(c.expected(), "observedStatusCode");
            String actualCode = str(c.observations(), "observedStatusCode");
            if (expectedCode != null && expectedCode.equals(actualCode)) statusCorrect++;
            String errorType = str(c.observations(), "errorType");
            String error = errorType == null ? "" : errorType.toUpperCase(java.util.Locale.ROOT);
            if ("TIMEOUT".equals(status) || error.contains("TIMEOUT")) timeouts++;
            if (error.contains("CONNECTION") || error.contains("CONNECT") || error.contains("UNKNOWN_HOST")
                    || error.contains("IO_FAILURE")) {
                connFailures++;
            }
            Object d = c.observed("durationMs");
            if (d instanceof Number n) latencies.add(n.doubleValue());
        }
        List<String> notes = new ArrayList<>();
        if (executed.isEmpty()) {
            notes.add("no runtime case was executed: the local sample target was not reachable");
        }
        notes.add("latency figures are raw observations, not a quality rating");
        return new RuntimeMetrics(cases.size(), executed.size(), passed, failed, skipped,
                execSuccess, statusCorrect, timeouts, connFailures, latency(latencies), notes);
    }

    private static DoubleLatency latency(List<Double> values) {
        if (values.isEmpty()) return new DoubleLatency(0, null, null, null, null);
        List<Double> sorted = values.stream().sorted().toList();
        double sum = 0;
        for (double v : sorted) sum += v;
        int n = sorted.size();
        double median = n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
        return new DoubleLatency(n, sorted.get(0), sorted.get(n - 1), sum / n, median);
    }

    /** Mutation metrics: a mutation passes when its declared required effect is detected. */
    public static MutationMetrics mutation(List<CaseOutcome> cases) {
        List<CaseOutcome> executed = cases.stream().filter(c -> !c.skipped()).toList();
        int correct = (int) executed.stream().filter(CaseOutcome::passed).count();
        List<String> effects = new ArrayList<>();
        for (CaseOutcome c : cases) {
            effects.add(c.caseId() + "=" + c.status());
        }
        List<String> notes = new ArrayList<>();
        if (executed.isEmpty()) notes.add("no mutation case was executed");
        notes.add("mutations are applied to an in-memory copy of the base input; no repository file is modified");
        notes.add("a mutation case fails when the required effect declared in the fixture is NOT observed");
        return new MutationMetrics(cases.size(), executed.size(), correct,
                executed.size() - correct, effects, notes);
    }

    /** Reads a scalar field from an expected/actual/observation map, as text. */
    private static String str(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v == null || v instanceof com.fasterxml.jackson.databind.node.NullNode) return null;
        return String.valueOf(v);
    }

    /** Category tally: the observed category list is stored as a JSON array text by the runner. */
    private static List<String> categories(Map<String, Object> map, String key) {
        Object v = map == null ? null : map.get(key);
        List<String> out = new ArrayList<>();
        if (v instanceof String s && s.startsWith("[")) {
            for (String part : s.substring(1, s.length() - 1).split(",")) {
                if (!part.isBlank()) out.add(part.trim());
            }
        }
        return out;
    }
}