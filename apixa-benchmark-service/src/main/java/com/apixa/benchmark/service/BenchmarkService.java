package com.apixa.benchmark.service;

import com.apixa.benchmark.model.BenchmarkCase;
import com.apixa.benchmark.model.BenchmarkResult;
import com.apixa.benchmark.model.BenchmarkRunRequest;
import com.apixa.benchmark.model.BenchmarkSuite;
import com.apixa.benchmark.engine.BenchmarkFixtureLoader;
import com.apixa.benchmark.engine.BenchmarkMetrics;
import com.apixa.benchmark.engine.BenchmarkRunners;
import com.apixa.benchmark.engine.CaseOutcome;
import com.apixa.benchmark.engine.MutationEngine;
import com.apixa.common.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step 15 benchmark orchestration.
 *
 * <p>Stateless by design: the ground truth is a source-controlled fixture under {@code samples/benchmark/}
 * and results are returned in the response. Nothing is written to a database or to the repository, so a
 * benchmark run can never pollute the working tree.
 *
 * <p>Execution status and case outcome are kept strictly apart: {@code FAILED} status means the harness
 * itself could not run; a case that disagrees with the fixture is a <b>case</b> failure.
 */
@Service
public class BenchmarkService {

    private final BenchmarkFixtureLoader loader;
    private final BenchmarkRunners runners;
    private final MutationEngine mutations;

    @Value("${apixa.benchmark.fixture-path:samples/benchmark/step15-benchmark-cases.json}")
    private String fixturePath;

    @Value("${apixa.benchmark.determinism-repeats:3}")
    private int determinismRepeats;

    public BenchmarkService(BenchmarkFixtureLoader loader, BenchmarkRunners runners, MutationEngine mutations) {
        this.loader = loader;
        this.runners = runners;
        this.mutations = mutations;
    }

    public BenchmarkResult run(BenchmarkRunRequest request) {
        long started = System.nanoTime();
        BenchmarkSuite suite = loader.load();
        List<BenchmarkCase> selected = select(suite, request);
        if (selected.isEmpty()) {
            throw ApiException.badRequest("No benchmark case matched the request");
        }

        List<CaseOutcome> outcomes = new ArrayList<>();
        List<String> executionErrors = new ArrayList<>();
        for (BenchmarkCase c : selected) {
            try {
                outcomes.add(execute(c, suite));
            } catch (RuntimeException e) {
                executionErrors.add(c.caseId() + ": " + e.getClass().getSimpleName());
                outcomes.add(new CaseOutcome(c.caseId(), c.group(), c.description(), CaseOutcome.FAILED,
                        Map.of("declared", String.valueOf(c.expected())), Map.of("error", String.valueOf(e.getMessage())),
                        "case execution error", null, Map.of()));
            }
        }

        BenchmarkResult.Determinism determinism = determinism(selected, suite, request);
        return assemble(suite, selected, outcomes, determinism, executionErrors, started);
    }

    /** Runs one case through its group runner, resolving MUTATION against its declared base case. */
    private CaseOutcome execute(BenchmarkCase c, BenchmarkSuite suite) {
        if ("MUTATION".equals(c.group())) {
            BenchmarkCase base = find(suite, c.baseCaseId());
            if (base == null) {
                throw ApiException.badRequest("Mutation case " + c.caseId() + " references unknown base case "
                        + c.baseCaseId());
            }
            return mutations.run(c, base);
        }
        return runners.execute(c);
    }

    private BenchmarkCase find(BenchmarkSuite suite, String caseId) {
        if (caseId == null) return null;
        return suite.cases().stream().filter(x -> caseId.equals(x.caseId())).findFirst().orElse(null);
    }

    /** Registered case ids, groups and descriptions, in fixture order. Executes nothing. */
    public List<Map<String, Object>> listCases() {
        return loader.load().cases().stream()
                .map(c -> Map.<String, Object>of(
                        "caseId", c.caseId(),
                        "group", c.group(),
                        "description", c.description() == null ? "" : c.description()))
                .toList();
    }

    /** Deterministic selection: fixture order is preserved and unknown case ids are a 4xx. */
    private List<BenchmarkCase> select(BenchmarkSuite suite, BenchmarkRunRequest request) {
        List<String> ids = request.caseIds();
        List<String> groups = request.groups();
        if (!ids.isEmpty()) {
            for (String id : ids) {
                if (find(suite, id) == null) throw ApiException.badRequest("Unknown benchmark case: " + id);
            }
        }
        List<BenchmarkCase> out = new ArrayList<>();
        for (BenchmarkCase c : suite.cases()) {
            boolean byId = ids.isEmpty() || ids.contains(c.caseId());
            boolean byGroup = groups.isEmpty() || groups.contains(c.group());
            if (byId && byGroup) out.add(c);
        }
        return out;
    }

    /**
     * Repeatability check: the deterministic groups (no network, no generated ids) are executed several
     * times and their extracted actual values plus their ordering are compared.
     *
     * <p>Comparison excludes the intentionally varying metadata (Step 13 {@code verificationId},
     * {@code observedAt}, and the per-case wall-clock {@code durationMs}), which is listed in the result
     * so a reader can see exactly what was excluded and why.
     */
    private BenchmarkResult.Determinism determinism(List<BenchmarkCase> selected, BenchmarkSuite suite,
                                                    BenchmarkRunRequest request) {
        int repeats = request.repeatCount() > 1 ? request.repeatCount() : Math.max(2, determinismRepeats);
        List<BenchmarkCase> deterministic = selected.stream()
                .filter(c -> "CONFORMANCE".equals(c.group()) || "MAPPING".equals(c.group())
                        || "IMPACT".equals(c.group()) || "MUTATION".equals(c.group()))
                .toList();
        if (deterministic.isEmpty()) {
            return new BenchmarkResult.Determinism(repeats, true, true, List.of(),
                    VARYING_METADATA, "No deterministic (non-network) case was selected; nothing to compare.");
        }
        List<List<String>> signatures = new ArrayList<>();
        for (int i = 0; i < repeats; i++) {
            List<String> signature = new ArrayList<>();
            for (BenchmarkCase c : deterministic) {
                // A case that cannot be re-executed is recorded as such; it never aborts the run.
                String actual;
                try {
                    actual = String.valueOf(execute(c, suite).actual());
                } catch (RuntimeException e) {
                    actual = "ERROR:" + e.getClass().getSimpleName();
                }
                signature.add(c.caseId() + "|" + actual);
            }
            signatures.add(signature);
        }
        boolean contentSame = signatures.stream().allMatch(s -> s.equals(signatures.get(0)));
        List<String> firstOrder = ids(signatures.get(0));
        boolean orderSame = signatures.stream().allMatch(s -> ids(s).equals(firstOrder));
        return new BenchmarkResult.Determinism(repeats, contentSame, orderSame,
                deterministic.stream().map(BenchmarkCase::caseId).toList(), VARYING_METADATA,
                "Compared " + repeats + " executions of " + deterministic.size()
                        + " deterministic cases: extracted results and case ordering. Intentionally generated"
                        + " metadata (" + String.join(", ", VARYING_METADATA) + ") is excluded.");
    }

    private List<String> ids(List<String> signature) {
        return signature.stream().map(s -> s.substring(0, s.indexOf('|'))).toList();
    }

    /** Fields generated per run by design, therefore not compared for determinism. */
    private static final List<String> VARYING_METADATA = List.of("durationMs", "verificationId", "observedAt");
    private BenchmarkResult assemble(BenchmarkSuite suite, List<BenchmarkCase> selected,
                                     List<CaseOutcome> outcomes, BenchmarkResult.Determinism determinism,
                                     List<String> executionErrors, long startedNanos) {
        int passed = (int) outcomes.stream().filter(CaseOutcome::passed).count();
        int failed = (int) outcomes.stream().filter(o -> !o.passed() && !o.skipped()).count();
        int skipped = (int) outcomes.stream().filter(CaseOutcome::skipped).count();
        int executed = passed + failed;

        // Execution status vs case outcome: a mismatching case never turns the run itself FAILED.
        String status = executionErrors.isEmpty() ? BenchmarkResult.COMPLETED : BenchmarkResult.FAILED;
        if (status.equals(BenchmarkResult.COMPLETED) && skipped > 0) {
            status = BenchmarkResult.PARTIALLY_EXECUTED;
        }
        String interpretation = "Evaluation performed on " + executed + " controlled benchmark cases"
                + (skipped > 0 ? " (" + skipped + " skipped: a required local target was unavailable)" : "")
                + ". This measures only the supplied controlled fixture population and is not a claim about"
                + " APIXA accuracy in general.";

        BenchmarkResult.Metrics metrics = new BenchmarkResult.Metrics(
                BenchmarkMetrics.conformance(byGroup(outcomes, "CONFORMANCE")),
                BenchmarkMetrics.mapping(byGroup(outcomes, "MAPPING")),
                BenchmarkMetrics.impact(byGroup(outcomes, "IMPACT")),
                BenchmarkMetrics.runtime(byGroup(outcomes, "RUNTIME")),
                BenchmarkMetrics.mutation(byGroup(outcomes, "MUTATION")));

        List<BenchmarkResult.CaseResult> cases = outcomes.stream()
                .map(o -> new BenchmarkResult.CaseResult(o.caseId(), o.group(), o.description(),
                        o.status(), o.expected(), o.actual(), o.discrepancy(), o.durationMs()))
                .toList();

        return new BenchmarkResult(suite.benchmarkId(), status, interpretation, loader.describeFixture(),
                selected.size(), executed, skipped, passed, failed,
                executed == 0 ? null : (double) passed / executed,
                metrics, determinism, duration(outcomes), cases);
    }

    private List<CaseOutcome> byGroup(List<CaseOutcome> outcomes, String group) {
        return outcomes.stream().filter(o -> group.equals(o.group())).toList();
    }

    /** Descriptive wall-clock statistics of the cases. Never turned into a performance rating. */
    private BenchmarkResult.DurationStats duration(List<CaseOutcome> outcomes) {
        List<Long> values = outcomes.stream().map(CaseOutcome::durationMs)
                .filter(java.util.Objects::nonNull).sorted().toList();
        if (values.isEmpty()) return null;
        double sum = 0;
        for (long v : values) sum += v;
        int n = values.size();
        long median = n % 2 == 1 ? values.get(n / 2) : (values.get(n / 2 - 1) + values.get(n / 2)) / 2;
        return new BenchmarkResult.DurationStats(n, values.get(0), values.get(n - 1), sum / n, (double) median);
    }
}