package com.apixa.sourceanalysis.service;

import com.apixa.common.error.ApiException;
import com.apixa.sourceanalysis.engine.SpringSourceAnalyzer;
import com.apixa.sourceanalysis.model.SourceAnalysisResultDto;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SourceAnalysisService {

    private final SpringSourceAnalyzer analyzer;
    private final Map<String, SourceAnalysisResultDto> results = new ConcurrentHashMap<>();

    public SourceAnalysisService(SpringSourceAnalyzer analyzer) { this.analyzer = analyzer; }

    public SourceAnalysisResultDto analyze(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            throw ApiException.badRequest("projectPath is required");
        }
        Path path = Path.of(projectPath);
        if (!Files.isDirectory(path)) {
            throw ApiException.badRequest("Source path is not a directory: " + projectPath);
        }
        SourceAnalysisResultDto result = analyzer.analyze(projectPath);
        results.put(resultId(result), result);
        return result;
    }

    public Map<String, SourceAnalysisResultDto> all() { return new ConcurrentHashMap<>(results); }

    public SourceAnalysisResultDto get(String id) {
        SourceAnalysisResultDto r = results.get(id);
        if (r == null) throw ApiException.notFound("Source analysis result " + id + " not found");
        return r;
    }

    private String resultId(SourceAnalysisResultDto result) {
        return "SRC-" + Integer.toHexString(result.projectPath().hashCode());
    }
}
