package com.apixa.evidence.controller;

import com.apixa.evidence.entity.EvidenceEntity;
import com.apixa.evidence.service.EvidenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/{code}")
    public EvidenceEntity byCode(@PathVariable String code) { return service.byCode(code); }
}
