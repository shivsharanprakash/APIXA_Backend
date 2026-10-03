package com.apixa.impact.controller;

import com.apixa.impact.model.ImpactRequest;
import com.apixa.impact.model.ImpactResult;
import com.apixa.impact.service.ImpactService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Step 14 change-impact API (port 8088).
 *
 * <p>{@code POST /api/impact/analyze} compares two supplied APIXA snapshots and returns the factual
 * differences. Nothing is re-analysed and no other APIXA service is called, so the service is stateless
 * and independently testable.
 */
@RestController
@RequestMapping("/api/impact")
public class ImpactController {

    private final ImpactService service;

    public ImpactController(ImpactService service) { this.service = service; }

    @PostMapping("/analyze")
    public ImpactResult analyze(@RequestBody ImpactRequest request) {
        return service.analyze(request);
    }
}