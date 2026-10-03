package com.apixa.conformance.controller;

import com.apixa.common.model.SecurityPolicy;
import com.apixa.conformance.engine.SecurityConformanceEngine;
import com.apixa.conformance.engine.SecurityConformanceEngine.Finding;
import com.apixa.conformance.model.ConformanceRequest;
import com.apixa.conformance.model.ConformanceResultDto;
import com.apixa.conformance.service.ConformanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Step 11 conformance API (port 8085).
 *
 * <ul>
 *   <li>{@code POST /api/conformance/compare} — pre-existing single policy-vs-policy comparison,
 *       unchanged.</li>
 *   <li>{@code POST /api/conformance/compare-batch} — pre-existing batch comparison. Each item is
 *       compared exactly once (the previous implementation invoked the engine twice per item).</li>
 *   <li>{@code POST /api/conformance/analyze} — Step 11 endpoint mapping of Step 7 contract
 *       endpoints + Step 10 mapping results + Step 9 source security rules into conformance results.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/conformance")
public class ConformanceController {

    public record CompareRequest(SecurityPolicy expected, SecurityPolicy implemented) {}
    public record FindingDto(String method, String path, String result, Object expected, Object implemented,
                             String reason, List<String> evidenceIds) {}

    private final SecurityConformanceEngine engine;
    private final ConformanceService service;

    public ConformanceController(SecurityConformanceEngine engine, ConformanceService service) {
        this.engine = engine;
        this.service = service;
    }

    @PostMapping("/compare")
    public Finding compare(@RequestBody CompareRequest request) {
        return engine.compare(request.expected(), request.implemented());
    }

    @PostMapping("/compare-batch")
    public List<FindingDto> compareBatch(@RequestBody List<CompareRequest> requests) {
        if (requests == null) return List.of();
        // Exactly one comparison per item: the engine result is computed once and reused.
        return requests.stream().filter(java.util.Objects::nonNull).map(r -> {
            Finding f = engine.compare(r.expected(), r.implemented());
            return new FindingDto(null, null, f.result().name(), r.expected(), r.implemented(), f.reason(), null);
        }).toList();
    }

    /** Step 11 orchestration: contract endpoints + mapping results + source security rules. */
    @PostMapping("/analyze")
    public List<ConformanceResultDto> analyze(@Valid @RequestBody ConformanceRequest request) {
        return service.analyze(request);
    }
}
