package com.apixa.benchmark.engine;

import com.apixa.benchmark.model.BenchmarkCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step 15 execution harness: runs one ground-truth case against the real APIXA component and compares
 * the result with the expectation <b>declared in the fixture</b>.
 *
 * <p><b>Evaluation level.</b> Step 11, Step 10, Step 14 and Step 13 are invoked <b>in process</b>
 * through their existing public service classes ({@code ConformanceService}, {@code MappingService},
 * {@code ImpactService}, {@code RuntimeVerificationService}). No HTTP call to another APIXA service is
 * made, so the benchmark cannot become unstable because a neighbouring service is down, and it can
 * never trigger a second analysis run: every runner passes data that is already structured, exactly
 * like the real API endpoints do.
 *
 * <p>The only network traffic is Step 13's own bounded HTTP request to the local sample target
 * ({@code samples/runtime-sample}); it is never an external host.
 *
 * <p><b>No self-validation.</b> Nothing here computes an expectation. {@link BenchmarkCase#expected()}
 * is read from the fixture and compared; the components under test are called only for the actual side.
 */
@Component
public class BenchmarkHarness {

    private final ObjectMapper mapper;

    public BenchmarkHarness(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() { return mapper; }

    /** Result of a group-specific runner before it is turned into a {@link CaseOutcome}. */
    public record Measured(boolean matched, boolean skipped, Map<String, Object> expected,
                           Map<String, Object> actual, String discrepancy, Map<String, Object> observations) {}
    /**
     * Shared comparison rule: every field present in the fixture's {@code expected} object must equal
     * the corresponding value the component returned. A field is never skipped silently, and extra
     * actual fields never fail a case on their own.
     */
    public boolean matches(JsonNode expected, JsonNode actual, List<String> assertFields) {
        List<String> fields = new ArrayList<>();
        expected.fieldNames().forEachRemaining(fields::add);
        if (assertFields != null && !assertFields.isEmpty()) fields.retainAll(assertFields);
        if (fields.isEmpty()) return false;   // a case must assert something
        for (String f : fields) {
            if (!equalNode(expected.get(f), actual == null ? null : actual.get(f))) return false;
        }
        return true;
    }

    /** Concise, human-readable difference between the declared expectation and the actual result. */
    public String describe(JsonNode expected, JsonNode actual) {
        StringBuilder sb = new StringBuilder();
        expected.fieldNames().forEachRemaining(f -> {
            JsonNode e = expected.get(f);
            JsonNode a = actual == null ? null : actual.get(f);
            if (!equalNode(e, a)) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(f).append(": expected=").append(text(e)).append(", actual=").append(text(a));
            }
        });
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Arrays compare element-wise and in order; everything else compares structurally. */
    private boolean equalNode(JsonNode e, JsonNode a) {
        if (e == null || e.isNull()) return a == null || a.isNull();
        if (a == null || a.isNull()) return false;
        if (e.isArray() && a.isArray()) {
            if (e.size() != a.size()) return false;
            for (int i = 0; i < e.size(); i++) if (!equalNode(e.get(i), a.get(i))) return false;
            return true;
        }
        return e.equals(a);
    }

    private String text(JsonNode n) {
        if (n == null || n.isNull()) return "null";
        return n.isArray() ? n.toString() : n.asText();
    }

    /** Converts a fixture {@code input} object into a typed request object of the component under test. */
    public <T> T convert(JsonNode input, Class<T> type) {
        return mapper.convertValue(input, type);
    }
}