package com.apixa.benchmark.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Step 15 benchmark suite: the ground-truth fixture set, loaded verbatim from
 * {@code samples/benchmark/step15-benchmark-cases.json}.
 *
 * <p><b>The fixture is the only source of truth.</b> Every {@link BenchmarkCase} carries its own explicit
 * {@code expected} values, authored independently of APIXA. The runner never derives an expectation
 * from the system under test, so {@code expected = engine.compare(input)} followed by
 * {@code actual = engine.compare(input)} — the classic self-validating benchmark — is impossible here.
 *
 * <p>Inputs are compact JSON structures that map directly onto the existing Step 7/8/10/11/13/14
 * request shapes. No OpenAPI document and no Java source file is embedded in a case.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BenchmarkSuite(
        String benchmarkId,
        String description,
        /** Deterministic execution order of the cases, exactly as declared in the fixture. */
        List<BenchmarkCase> cases) {}