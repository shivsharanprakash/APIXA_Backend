package com.apixa.benchmark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * One ground-truth benchmark case.
 *
 * <p>{@code input} is the data handed to the component under test; {@code expected} states, in advance,
 * what the component must return. The two are never mixed: the runner reads {@code expected} only to
 * compare, never to influence execution.
 *
 * <p>{@code group} selects which Step 15 harness executes the case:
 * {@code CONFORMANCE} (Step 11), {@code MAPPING} (Step 10), {@code IMPACT} (Step 14),
 * {@code RUNTIME} (Step 13), {@code MUTATION} (controlled mutation of another case's input) and
 * {@code DETERMINISM} (repeatability of an already declared case).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BenchmarkCase(
        String caseId,
        String group,
        String description,
        JsonNode input,
        /** Expected values, authored in the fixture. For MUTATION cases: the required effect. */
        JsonNode expected,
        /** MUTATION/DETERMINISM only: the case this one is derived from. */
        String baseCaseId,
        /** Which expected fields must agree; null means "every field present in expected". */
        List<String> assertFields,
        /** RUNTIME only: local target the case needs, purely informational in the result. */
        String note) {}