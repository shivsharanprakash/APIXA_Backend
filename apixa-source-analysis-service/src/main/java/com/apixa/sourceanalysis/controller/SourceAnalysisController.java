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

    @GetMapping
    public Map<String, SourceAnalysisResultDto> list() { return service.all(); }

    @GetMapping("/{id}")
    public SourceAnalysisResultDto get(@PathVariable String id) { return service.get(id); }
}
