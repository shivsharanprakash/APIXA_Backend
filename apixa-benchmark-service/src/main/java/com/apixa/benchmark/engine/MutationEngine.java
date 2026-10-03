package com.apixa.benchmark.engine;

import com.apixa.benchmark.model.BenchmarkCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Step 15 controlled mutation benchmark.
 *
 * <p><b>Safety first.</b> A mutation is applied to an <b>in-memory deep copy</b> of a base case's
 * {@code input} JSON tree. No file on disk is read-modified-written, no fixture is rewritten, no
 * repository source is touched and no temporary file is created Ã¢â‚¬â€ so there is nothing to clean up and
 * nothing that can leak a mutated artifact into the working tree. The base case object itself is never
 * modified, so a mutation case can never influence a later case in the same run.
 *
 * <p>Every mutation declares its <b>required effect</b> in the fixture. Only the detection of that effect
 * is scored: the benchmark measures APIXA, it does not decide whether an effect is desirable.
 *
 * <p>Supported mutations (deterministic, no randomness anywhere):
 * {@code SET_ROLES}, {@code SET_PERMIT_ALL}, {@code SET_AUTHENTICATION},
 * {@code SET_CONFORMANCE_STATUS}, {@code REMOVE_ENDPOINT}, {@code ADD_ENDPOINT}.
 * Each addresses its target with an explicit JSON pointer into the copied input.
 */
@Component
public class MutationEngine {

    private final ObjectMapper mapper;
    private final BenchmarkRunners runners;

    public MutationEngine(ObjectMapper mapper, BenchmarkRunners runners) {
        this.mapper = mapper;
        this.runners = runners;
    }

    /**
     * Builds the mutated case from its base case. The returned case is a new object holding a mutated
     * <b>copy</b> of the input; the base case is untouched.
     */
    public BenchmarkCase mutate(BenchmarkCase base, JsonNode mutation, com.fasterxml.jackson.databind.JsonNode mutationCaseExpected, List<String> mutationCaseAssertFields) {
        ObjectNode copy = mapper.createObjectNode();
        copy.setAll((ObjectNode) base.input().deepCopy());
        String op = mutation.path("op").asText();
        switch (op) {
            case "SET_ROLES" -> setRoles(copy, mutation);
            case "SET_PERMIT_ALL" -> setPolicyField(copy, mutation, "permitAll",
                    mutation.path("value").asBoolean());
            case "SET_AUTHENTICATION" -> setPolicyField(copy, mutation, "authentication",
                    mutation.path("value").asText());
            case "SET_OPERATOR" -> setField(copy, mutation, "operator", mutation.path("value").asText());
            case "SET_CONFORMANCE_STATUS" -> setConformance(copy, mutation);
            case "REMOVE_ENDPOINT" -> removeEndpoint(copy, mutation);
            case "ADD_ENDPOINT" -> addEndpoint(copy, mutation);
            default -> throw new IllegalArgumentException("Unsupported mutation op: " + op);
        }
        // The mutated case keeps the BASE input and the MUTATION case's required effect as its
        // expectation: a mutation is scored against the effect it declares, not against the base verdict.
        return new BenchmarkCase(base.caseId(), base.group(), base.description(), copy,
                mutationCaseExpected, base.caseId(), mutationCaseAssertFields, base.note());
    }
    /** Applies a mutation case and reports whether the declared required effect was observed. */
    public CaseOutcome run(BenchmarkCase mutationCase, BenchmarkCase base) {
        CaseOutcome outcome = runners.execute(mutate(base, mutationCase.input(), mutationCase.expected(), mutationCase.assertFields()));
        if (outcome.skipped()) {
            return new CaseOutcome(mutationCase.caseId(), "MUTATION", mutationCase.description(),
                    CaseOutcome.SKIPPED, outcome.expected(), outcome.actual(), outcome.discrepancy(),
                    outcome.durationMs(), outcome.observations());
        }
        boolean detected = outcome.passed();
        return new CaseOutcome(mutationCase.caseId(), "MUTATION",
                mutationCase.description() + " (mutation of " + base.caseId() + ")",
                detected ? CaseOutcome.PASSED : CaseOutcome.FAILED,
                outcome.expected(), outcome.actual(),
                detected ? null : "Required mutation effect not observed: " + outcome.discrepancy(),
                outcome.durationMs(), outcome.observations());
    }

    private List<String> policyPaths(JsonNode mutation) {
        List<String> paths = new ArrayList<>();
        if (mutation.hasNonNull("path")) paths.add(mutation.get("path").asText());
        return paths;
    }

    /**
     * Replaces the role list addressed by each JSON pointer. The pointer may address either the policy
     * object (its {@code roles} array is replaced) or the {@code roles} array itself.
     */
    private void setRoles(JsonNode root, JsonNode mutation) {
        List<String> roles = new ArrayList<>();
        mutation.path("roles").forEach(r -> roles.add(r.asText()));
        for (String pointer : policyPaths(mutation)) {
            JsonNode target = root.at(pointer);
            ArrayNode arr = mapper.createArrayNode();
            roles.forEach(arr::add);
            if (target instanceof ArrayNode) {
                // Address the parent explicitly: ObjectNode#replace takes a field name, not a pointer.
                int cut = pointer.lastIndexOf('/');
                JsonNode parent = cut <= 0 ? root : root.at(pointer.substring(0, cut));
                if (parent instanceof ObjectNode p) p.replace(pointer.substring(cut + 1), arr);
                else throw new IllegalArgumentException("SET_ROLES parent not found: " + pointer);
            } else if (target instanceof ObjectNode p) {
                p.set("roles", arr);
            } else {
                throw new IllegalArgumentException("SET_ROLES path not found: " + pointer);
            }
        }
    }

    private void setPolicyField(JsonNode root, JsonNode mutation, String field, Object value) {
        for (String pointer : policyPaths(mutation)) {
            JsonNode policy = root.at(pointer);
            if (!(policy instanceof ObjectNode p)) {
                throw new IllegalArgumentException("Policy path not found: " + pointer);
            }
            if (value instanceof Boolean b) p.put(field, b);
            else p.put(field, String.valueOf(value));
        }
    }

    /** Sets a text field on each addressed object (e.g. a Step 9 source security rule operator). */
    private void setField(JsonNode root, JsonNode mutation, String field, String value) {
        for (String pointer : policyPaths(mutation)) {
            if (root.at(pointer) instanceof ObjectNode o) o.put(field, value);
            else throw new IllegalArgumentException("Path not found: " + pointer);
        }
    }

    private void setConformance(JsonNode root, JsonNode mutation) {
        for (String pointer : policyPaths(mutation)) {
            if (root.at(pointer) instanceof ObjectNode o) o.put("conformanceStatus", mutation.path("value").asText());
        }
    }

    private void removeEndpoint(JsonNode root, JsonNode mutation) {
        JsonNode list = root.at("/" + mutation.path("version").asText("v2") + "/endpoints");
        int index = mutation.path("index").asInt();
        if (list instanceof ArrayNode arr && index >= 0 && index < arr.size()) arr.remove(index);
    }

    private void addEndpoint(JsonNode root, JsonNode mutation) {
        JsonNode list = root.at("/" + mutation.path("version").asText("v2") + "/endpoints");
        if (list instanceof ArrayNode arr) arr.add(mapper.convertValue(mutation.path("endpoint"), ObjectNode.class));
    }

    /** Provenance of a mutation, reported with the results. */
    public Map<String, Object> effectOf(String op) {
        return Map.of("op", op, "source", "in-memory copy of the base case input; no file is modified");
    }
}