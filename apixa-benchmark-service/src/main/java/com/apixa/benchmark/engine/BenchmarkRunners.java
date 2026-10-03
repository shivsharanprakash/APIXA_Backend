package com.apixa.benchmark.engine;

import com.apixa.benchmark.model.BenchmarkCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
/**
 * The four group runners. Each one posts the case input to the corresponding APIXA service on localhost
 * and reduces the response to a small JSON object of observed facts, which is then compared with the
 * fixture's declared {@code expected}.
 *
 * <p>None of these runners modifies an analyzed component, and none re-derives an expectation: they only
 * report what the component returned.
 */
@Component
public class BenchmarkRunners {

    private final ObjectMapper mapper;
    private final BenchmarkHarness harness;
    private final LocalServiceClient client;

    public BenchmarkRunners(ObjectMapper mapper, BenchmarkHarness harness, LocalServiceClient client) {
        this.mapper = mapper;
        this.harness = harness;
        this.client = client;
    }
/**
     * Executes one case. Never throws for a case-level problem: an execution error becomes a FAILED case
     * with a concise discrepancy, so a single broken case cannot abort the whole benchmark.
     */
    public CaseOutcome execute(BenchmarkCase testCase) {
        long started = System.nanoTime();
        try {
            List<String> fields = testCase.assertFields();
            BenchmarkHarness.Measured m = switch (testCase.group()) {
                case "CONFORMANCE" -> conformance(testCase, fields);
                case "MAPPING" -> mapping(testCase, fields);
                case "IMPACT" -> impact(testCase, fields);
                case "RUNTIME" -> runtime(testCase, fields);
                default -> throw new IllegalArgumentException("Unsupported benchmark group: " + testCase.group());
            };
            return toOutcome(testCase, m, started);
        } catch (RuntimeException e) {
            String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return new CaseOutcome(testCase.caseId(), testCase.group(), testCase.description(),
                    CaseOutcome.FAILED, Map.of("declared", String.valueOf(testCase.expected())),
                    Map.of("error", reason), reason, null, Map.of());
        }
    }

    private CaseOutcome toOutcome(BenchmarkCase testCase, BenchmarkHarness.Measured m, long startedNanos) {
        long ms = (System.nanoTime() - startedNanos) / 1_000_000L;
        String status = m.skipped() ? CaseOutcome.SKIPPED
                : m.matched() ? CaseOutcome.PASSED : CaseOutcome.FAILED;
        return new CaseOutcome(testCase.caseId(), testCase.group(), testCase.description(), status,
                m.expected(), m.actual(), m.matched() ? null : m.discrepancy(), ms, m.observations());
    }

        /** Step 11 via {@code POST /api/conformance/analyze} on the local conformance service. */
    public BenchmarkHarness.Measured conformance(BenchmarkCase testCase, List<String> fields) {
        return guarded(testCase, fields, () -> {
            JsonNode first = client.postFirst(client.conformanceBaseUrl(), "/api/conformance/analyze",
                    testCase.input());
            ObjectNode actual = mapper.createObjectNode();
            if (first != null) {
                actual.put("mappingStatus", first.path("mappingStatus").asText(null));
                actual.put("conformanceStatus", first.path("conformanceStatus").asText(null));
            }
            Map<String, Object> observations = new LinkedHashMap<>();
            observations.put("conformanceStatus", text(first, "conformanceStatus"));
            observations.put("mappingStatus", text(first, "mappingStatus"));
            return new Outcome(actual, observations, false, null);
        });
    }

    /** Step 10 via {@code POST /api/mappings/map} on the local mapping service. */
    public BenchmarkHarness.Measured mapping(BenchmarkCase testCase, List<String> fields) {
        return guarded(testCase, fields, () -> {
            JsonNode first = client.postFirst(client.mappingBaseUrl(), "/api/mappings/map", testCase.input());
            ObjectNode actual = mapper.createObjectNode();
            actual.put("status", first == null ? null : first.path("status").asText(null));
            actual.put("candidateCount", first == null ? 0 : first.path("candidates").size());
            Map<String, Object> observations = new LinkedHashMap<>();
            observations.put("mappingStatus", text(first, "status"));
            observations.put("candidateCount", first == null ? 0 : first.path("candidates").size());
            return new Outcome(actual, observations, false, null);
        });
    }

    /**
     * Step 14 via {@code POST /api/impact/analyze} on the local impact service. The observed value is the
     * ordered list of impact categories plus the record count. Step 14 defines no severity, so none is
     * derived here.
     */
    public BenchmarkHarness.Measured impact(BenchmarkCase testCase, List<String> fields) {
        return guarded(testCase, fields, () -> {
            JsonNode result = client.post(client.impactBaseUrl(), "/api/impact/analyze", testCase.input());
            ArrayNode categories = mapper.createArrayNode();
            Map<String, Integer> counts = new TreeMap<>();
            for (JsonNode r : result.path("impacts")) {
                String category = r.path("category").asText();
                categories.add(category);
                counts.merge(category, 1, Integer::sum);
            }
            ObjectNode actual = mapper.createObjectNode();
            actual.put("impactCount", result.path("impactCount").asInt());
            actual.set("categories", categories);
            Map<String, Object> observations = new LinkedHashMap<>();
            observations.put("impactCount", result.path("impactCount").asInt());
            observations.put("categorySet", List.copyOf(counts.keySet()));
            observations.put("categoryCounts", counts);
            return new Outcome(actual, observations, false, null);
        });
    }

    /**
     * Step 13 via {@code POST /api/runtime/verify} on the local runtime service. A case whose target is
     * not listening is SKIPPED: the sample target is an optional local process, never a fabricated pass.
     */
    public BenchmarkHarness.Measured runtime(BenchmarkCase testCase, List<String> fields) {
        if (testCase.expected().hasNonNull("observedStatusCode")
                && !reachable(testCase.input().path("targetBaseUrl").asText())) {
            ObjectNode actual = mapper.createObjectNode();
            actual.put("status", "NOT_EXECUTED");
            return new BenchmarkHarness.Measured(false, true,
                    Map.of("skipped", "local sample target not reachable"),
                    toMap(actual), "Local sample target is not running",
                    Map.of("skipped", true));
        }
        return guarded(testCase, fields, () -> {
            JsonNode result = client.post(client.runtimeBaseUrl(), "/api/runtime/verify", testCase.input());
            ObjectNode actual = mapper.createObjectNode();
            actual.put("status", result.path("status").asText(null));
            JsonNode code = result.path("response").path("statusCode");
            if (code.isMissingNode() || code.isNull()) actual.putNull("observedStatusCode");
            else actual.put("observedStatusCode", code.asInt());
            actual.put("errorType", result.path("error").path("type").asText(null));
            Map<String, Object> observations = new LinkedHashMap<>();
            observations.put("runtimeStatus", text(result, "status"));
            observations.put("observedStatusCode",
                    code.isMissingNode() || code.isNull() ? null : code.asInt());
            observations.put("errorType", text(result.path("error"), "type"));
            JsonNode durationNode = result.path("response").path("durationMs");
            observations.put("durationMs", durationNode.isMissingNode() || durationNode.isNull()
                    ? null : durationNode.asLong());
            return new Outcome(actual, observations, false, null);
        });
    }

    /** One runner body: the observed facts plus a marker for "target unavailable". */
    private record Outcome(ObjectNode actual, Map<String, Object> observations, boolean skipped, String skipReason) {}

    /**
     * Executes a runner body and turns it into a comparison result. A service that is not running becomes
     * SKIPPED; anything else that goes wrong becomes a normal FAILED case with a concise discrepancy.
     */
    private BenchmarkHarness.Measured guarded(BenchmarkCase testCase, List<String> fields, Body body) {
        Outcome outcome;
        try {
            outcome = body.run();
        } catch (LocalServiceClient.ServiceUnavailableException e) {
            ObjectNode actual = mapper.createObjectNode();
            actual.put("status", "SERVICE_UNAVAILABLE");
            return new BenchmarkHarness.Measured(false, true,
                    Map.of("skipped", e.getMessage()), toMap(actual), e.getMessage(),
                    Map.of("skipped", true));
        }
        if (outcome.skipped()) {
            return new BenchmarkHarness.Measured(false, true, Map.of("skipped", outcome.skipReason()),
                    toMap(outcome.actual()), outcome.skipReason(), outcome.observations());
        }
        JsonNode expected = testCase.expected();
        boolean ok = harness.matches(expected, outcome.actual(), fields);
        return new BenchmarkHarness.Measured(ok, false, toMap(expected), toMap(outcome.actual()),
                ok ? null : harness.describe(expected, outcome.actual()), outcome.observations());
    }

    private interface Body { Outcome run(); }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }
private Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
        node.properties().forEach(e -> out.put(e.getKey(), e.getValue().isValueNode()
                ? (Object) e.getValue().asText() : e.getValue().toString()));
        return out;
    }

    /**
     * Cheap reachability probe for the optional local sample target. Only a local host is ever probed:
     * the runtime cases declare their own target, and no external host is contacted.
     */
    private boolean reachable(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            int port = uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), 500);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}