package com.apixa.benchmark.engine;

import com.apixa.benchmark.model.BenchmarkCase;
import com.apixa.benchmark.model.BenchmarkSuite;
import com.apixa.common.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads the Step 15 ground-truth suite from {@code samples/benchmark/step15-benchmark-cases.json}.
 *
 * <p>The fixture is read from disk on every run and is <b>never written, mutated or generated</b> by the
 * benchmark. A missing or unreadable fixture is a controlled 4xx/5xx error, never a silent empty run.
 */
@Component
public class BenchmarkFixtureLoader {

    private final ObjectMapper mapper;

    @Value("${apixa.benchmark.fixture-path:samples/benchmark/step15-benchmark-cases.json}")
    private String fixturePath;

    public BenchmarkFixtureLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public BenchmarkSuite load() {
        Path path = resolve();
        if (path == null) {
            throw ApiException.badRequest("Benchmark fixture not found: " + fixturePath
                    + " (resolved against the working directory and its parents;"
                    + " set apixa.benchmark.fixture-path to override)");
        }
        try {
            BenchmarkSuite suite = mapper.readValue(Files.readString(path), BenchmarkSuite.class);
            validate(suite);
            return suite;
        } catch (IOException e) {
            // Invalid benchmark definition: reported as a client error without a stack trace.
            throw ApiException.badRequest("Benchmark fixture is not valid: " + e.getMessage());
        }
    }

    /**
     * Resolves the fixture path. The configured path is tried first, then the same relative path from
     * each parent directory, so the benchmark works whether it is started from the repository root, from
     * the module directory or from an IDE.
     */
    private Path resolve() {
        Path direct = Path.of(fixturePath);
        if (Files.isRegularFile(direct)) return direct;
        Path relative = direct.isAbsolute() ? null : direct;
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && relative != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;
            dir = dir.getParent();
        }
        return null;
    }

    /** Absolute fixture location used by the result, for traceability. */
    public String describeFixture() {
        Path resolved = resolve();
        return resolved == null ? fixturePath : resolved.toString();
    }

    private void validate(BenchmarkSuite suite) {
        if (suite == null || suite.benchmarkId() == null || suite.benchmarkId().isBlank()) {
            throw ApiException.badRequest("Benchmark fixture has no benchmarkId");
        }
        List<BenchmarkCase> cases = suite.cases();
        if (cases == null || cases.isEmpty()) {
            throw ApiException.badRequest("Benchmark fixture declares no cases");
        }
        for (BenchmarkCase c : cases) {
            if (c.caseId() == null || c.caseId().isBlank()) {
                throw ApiException.badRequest("Every benchmark case needs a caseId");
            }
            if (c.group() == null || c.group().isBlank()) {
                throw ApiException.badRequest("Benchmark case " + c.caseId() + " has no group");
            }
            if (c.expected() == null || c.expected().isEmpty()) {
                // Ground truth is mandatory: a case without an explicit expectation cannot be scored.
                throw ApiException.badRequest("Benchmark case " + c.caseId() + " has no expected result");
            }
        }
    }
}