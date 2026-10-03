package com.apixa.benchmark.controller;

import com.apixa.benchmark.model.BenchmarkResult;
import com.apixa.benchmark.model.BenchmarkRunRequest;
import com.apixa.benchmark.service.BenchmarkService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Step 15 benchmark API (port 8089).
 *
 * <ul>
 *   <li>{@code POST /api/benchmark/run} — executes the ground-truth fixture suite (or a selected
 *       subset) against the real APIXA components and returns cases, metrics, confusion matrix and the
 *       determinism check.</li>
 *   <li>{@code GET /api/benchmark/cases} — the registered ground-truth case ids and groups, without
 *       running anything.</li>
 * </ul>
 *
 * <p>This is an evaluation harness, not a scheduling platform: no persistence, no scheduler, no
 * background job. Report generation, gateway and desktop integration are later steps and are
 * deliberately absent.
 */
@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private final BenchmarkService service;

    public BenchmarkController(BenchmarkService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public BenchmarkResult run(@RequestBody(required = false) BenchmarkRunRequest request) {
        return service.run(request == null ? new BenchmarkRunRequest(List.of(), List.of(), null) : request);
    }

    /** Registered case ids, groups and descriptions, in fixture order. Executes nothing. */
    @GetMapping("/cases")
    public Map<String, Object> cases() {
        return Map.of("cases", service.listCases());
    }
}