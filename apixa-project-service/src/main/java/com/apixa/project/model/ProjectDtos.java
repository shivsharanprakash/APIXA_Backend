package com.apixa.project.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record CreateProjectRequest(
            @NotBlank String name,
            String description,
            String apiName,
            String baseUrl,
            String sourcePath,
            String sourceHash) {}

    public record UpdateProjectRequest(
            String name,
            String description,
            String apiName,
            String baseUrl,
            String sourcePath,
            String sourceHash) {}

    public record CreateVersionRequest(
            @NotBlank String versionLabel,
            String openapiPath,
            String openapiContent,
            String notes) {}

    public record StartRunRequest(
            Long apiVersionId,
            String sourceHash,
            String analyzerVersion,
            Map<String, Object> configuration) {}

    public record UpdateRunStatusRequest(@NotBlank String status) {}

    public record ProjectDto(Long id, String name, String description, String apiName, String baseUrl,
                             String sourcePath, String sourceHash, String createdAt) {}

    public record ApiVersionDto(Long id, Long projectId, String versionLabel, String openapiPath,
                                String openapiHash, String notes, String createdAt) {}

    public record AnalysisRunDto(Long id, Long projectId, Long apiVersionId, String status,
                                 String openapiHash, String sourceHash, String analyzerVersion,
                                 String configuration, String resultSummary, String startedAt, String endedAt) {}
}
