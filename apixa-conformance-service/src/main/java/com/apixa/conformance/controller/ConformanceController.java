package com.apixa.conformance.controller;

import com.apixa.common.model.SecurityPolicy;
import com.apixa.conformance.engine.SecurityConformanceEngine;
import com.apixa.conformance.engine.SecurityConformanceEngine.Finding;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conformance")
public class ConformanceController {

    public record CompareRequest(SecurityPolicy expected, SecurityPolicy implemented) {}
    public record FindingDto(String method, String path, String result, Object expected, Object implemented,
                             String reason, List<String> evidenceIds) {}

    private final SecurityConformanceEngine engine;

    public ConformanceController(SecurityConformanceEngine engine) { this.engine = engine; }

    @PostMapping("/compare")
    public Finding compare(@RequestBody CompareRequest request) {
        return engine.compare(request.expected(), request.implemented());
    }

    @PostMapping("/compare-batch")
    public List<FindingDto> compareBatch(@RequestBody List<CompareRequest> requests) {
        return requests.stream()
                .map(r -> new FindingDto(null, null, engine.compare(r.expected(), r.implemented()).result().name(),
                        r.expected(), r.implemented(), engine.compare(r.expected(), r.implemented()).reason(), null))
                .toList();
    }
}
