package com.apixa.mapping.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MappingResultDto(
        String method,
        String path,
        String status,           // MATCHED, MULTIPLE_CANDIDATES, UNMATCHED, UNCERTAIN
        String confidence,       // HIGH, MEDIUM, LOW
        String implementationClass,
        String implementationMethod,
        String implementationPath,
        List<CandidateDto> candidates,
        String reason) {

    public record CandidateDto(String className, String methodName, String httpMethod, String path) {}
}
