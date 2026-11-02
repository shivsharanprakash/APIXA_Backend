package com.apixa.conformance.investigation.controller;

import com.apixa.conformance.investigation.model.*;
import com.apixa.conformance.investigation.service.InvestigationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investigation")
public class InvestigationController {

    private final InvestigationService service;

    public InvestigationController(InvestigationService service) {
        this.service = service;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public InvestigationSessionDto start(@Valid @RequestBody StartInvestigationRequest request) {
        return service.start(request);
    }

    @GetMapping("/{sessionId}")
    public InvestigationSessionDto get(@PathVariable String sessionId) {
        return service.get(sessionId);
    }

    @PostMapping("/{sessionId}/approve-test")
    public InvestigationSessionDto approveTest(@PathVariable String sessionId,
                                               @RequestBody(required = false) ApproveTestRequest request) {
        return service.approveTest(sessionId, request);
    }

    @PostMapping("/quick-investigate")
    public InvestigationSessionDto quickInvestigate(@Valid @RequestBody StartInvestigationRequest request) {
        // Run with autoExecuteTest = true for instant complete audit
        StartInvestigationRequest autoReq = new StartInvestigationRequest(
                request.projectId(),
                request.findingId(),
                request.method(),
                request.path(),
                request.contractId(),
                request.analysisId(),
                request.verdict(),
                request.expectedSecurity(),
                request.implementedSecurity(),
                request.implementationClass(),
                request.implementationMethod(),
                request.sourceFile(),
                request.sourceLine(),
                request.reason(),
                request.targetBaseUrl(),
                true
        );
        return service.start(autoReq);
    }
}
