package com.apixa.evidence.controller;

import com.apixa.evidence.entity.EvidenceEntity;
import com.apixa.evidence.model.EvidenceSetRequest;
import com.apixa.evidence.model.EvidenceSetResponse;
import com.apixa.evidence.service.EvidenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Evidence API (port 8086).
 *
 * <p>Pre-existing low-level CRUD is preserved unchanged: {@code POST/GET /api/evidence},
 * {@code GET /api/evidence/run/{runId}} and {@code GET /api/evidence/{code}}.
 *
 * <p>Step 12 adds only the two endpoints needed for linked evidence sets:
 * {@code POST /api/evidence/analyze} (create/refresh a linked chain) and
 * {@code GET /api/evidence/set/{setId}} (retrieve it). No duplicate CRUD routes were introduced.
 */
@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final EvidenceService service;

    public EvidenceController(EvidenceService service) { this.service = service; }

    @PostMapping
    public EvidenceEntity store(@Valid @RequestBody EvidenceEntity evidence) {
        if (evidence.getId() != null) evidence.setId(null);
        if (evidence.getEvidenceCode() == null || evidence.getEvidenceCode().isBlank()) {
            evidence.setEvidenceCode("EV-" + System.currentTimeMillis());
        }
        return service.store(evidence);
    }

    @GetMapping
    public List<EvidenceEntity> all() { return service.all(); }

    @GetMapping("/run/{runId}")
    public List<EvidenceEntity> byRun(@PathVariable Long runId) { return service.byRun(runId); }

    @GetMapping("/set/{setId}")
    public List<EvidenceEntity> bySet(@PathVariable String setId) { return service.bySet(setId); }

    @GetMapping("/{code}")
    public EvidenceEntity byCode(@PathVariable String code) { return service.byCode(code); }

    /** Step 12: create or refresh the linked CONTRACT -> MAPPING -> SOURCE -> CONFORMANCE chain. */
    @PostMapping("/analyze")
    public EvidenceSetResponse analyze(@RequestBody EvidenceSetRequest request) {
        return service.createSet(request);
    }
}
