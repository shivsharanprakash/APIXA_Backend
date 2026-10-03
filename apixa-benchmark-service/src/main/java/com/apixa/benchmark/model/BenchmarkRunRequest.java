package com.apixa.benchmark.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Step 15 benchmark run request: run the whole fixture suite, or a deterministic subset.
 *
 * <p>{@code caseIds} empty/null means "every case in the fixture, in fixture order".
 * {@code repeats} > 1 re-executes the selection and additionally reports a determinism comparison —
 * it never changes what is expected.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenchmarkRunRequest(
        List<String> caseIds,
        List<String> groups,
        /** Number of executions per selected case; 1 = single run. Used by the determinism group. */
        Integer repeats) {

    public List<String> caseIds() { return caseIds == null ? List.of() : caseIds; }
    public List<String> groups() { return groups == null ? List.of() : groups; }
    /** Repeat count for the determinism check; never below 1. */
    public int repeatCount() { return repeats == null || repeats < 1 ? 1 : repeats; }
}