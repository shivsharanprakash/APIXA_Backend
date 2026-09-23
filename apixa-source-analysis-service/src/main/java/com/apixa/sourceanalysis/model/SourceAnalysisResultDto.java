package com.apixa.sourceanalysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceAnalysisResultDto(
        String projectPath,
        String analyzerVersion,
        int analyzedFiles,
        List<SourceEndpointDto> endpoints,
        List<SecurityEvidenceDto> securityRules) {
}
