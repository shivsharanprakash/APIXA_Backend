package com.apixa.sourceanalysis.controller;

import com.apixa.sourceanalysis.model.SourceAnalysisResultDto;
import com.apixa.sourceanalysis.service.SourceAnalysisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/source")
public class SourceAnalysisController {

    public record AnalyzeRequest(@NotBlank String projectPath) {}

    private final SourceAnalysisService service;

    public SourceAnalysisController(SourceAnalysisService service) { this.service = service; }

    @PostMapping("/analyze")
    public SourceAnalysisResultDto analyze(@Valid @RequestBody AnalyzeRequest request) {
        return service.analyze(request.projectPath());
    }

    /**
     * Step 8 source endpoint analysis: the HTTP endpoints declared by the Spring source of the
     * supplied local project directory. Discovery only — no security, no OpenAPI comparison.
     */
    @PostMapping("/analyze/endpoints")
    public SourceAnalysisResultDto analyzeEndpoints(@Valid @RequestBody AnalyzeRequest request) {
        return service.analyzeEndpoints(request.projectPath());
    }

    /**
     * Step 9 source security analysis: the security rules declared by the Spring source of the
     * supplied local project directory. Security discovery only — never compared with OpenAPI,
     * never mapped to endpoint paths (Step 10). Same request body as the other passes.
     */
    @PostMapping("/analyze/security")
    public SourceAnalysisResultDto analyzeSecurity(@Valid @RequestBody AnalyzeRequest request) {
        return service.analyzeSecurity(request.projectPath());
    }

    @GetMapping
    public Map<String, SourceAnalysisResultDto> list() { return service.all(); }

    @GetMapping("/{id}")
    public SourceAnalysisResultDto get(@PathVariable String id) { return service.get(id); }
}
