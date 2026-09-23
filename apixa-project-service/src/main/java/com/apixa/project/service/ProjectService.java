package com.apixa.project.service;

import com.apixa.common.error.ApiException;
import com.apixa.project.entity.*;
import com.apixa.project.model.ProjectDtos.*;
import com.apixa.project.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProjectService {

    private static final List<String> RUN_STATUSES = List.of("CREATED", "RUNNING", "COMPLETED", "FAILED");

    private final ProjectRepository projectRepository;
    private final ApiVersionRepository apiVersionRepository;
    private final AnalysisRunRepository analysisRunRepository;

    public ProjectService(ProjectRepository projectRepository, ApiVersionRepository apiVersionRepository,
                          AnalysisRunRepository analysisRunRepository) {
        this.projectRepository = projectRepository;
        this.apiVersionRepository = apiVersionRepository;
        this.analysisRunRepository = analysisRunRepository;
    }

    @Transactional
    public ProjectDto createProject(CreateProjectRequest req) {
        ProjectEntity e = new ProjectEntity();
        apply(e, req.name(), req.description(), req.apiName(), req.baseUrl(), req.sourcePath(), req.sourceHash());
        return toDto(projectRepository.save(e));
    }

    @Transactional
    public ProjectDto updateProject(Long id, UpdateProjectRequest req) {
        ProjectEntity e = projectRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Project " + id + " not found"));
        apply(e, req.name(), req.description(), req.apiName(), req.baseUrl(), req.sourcePath(), req.sourceHash());
        return toDto(projectRepository.save(e));
    }

    public ProjectDto getProject(Long id) {
        return projectRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> ApiException.notFound("Project " + id + " not found"));
    }

    public List<ProjectDto> listProjects() {
        return projectRepository.findAllByOrderByIdDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public void deleteProject(Long id) {
        if (!projectRepository.existsById(id)) {
            throw ApiException.notFound("Project " + id + " not found");
        }
        apiVersionRepository.findByProjectIdOrderByIdDesc(id).forEach(v -> apiVersionRepository.deleteById(v.getId()));
        analysisRunRepository.findByProjectIdOrderByIdDesc(id).forEach(r -> analysisRunRepository.deleteById(r.getId()));
        projectRepository.deleteById(id);
    }

    @Transactional
    public ApiVersionDto addVersion(Long projectId, CreateVersionRequest req) {
        requireProject(projectId);
        if (apiVersionRepository.existsByProjectIdAndVersionLabel(projectId, req.versionLabel())) {
            throw ApiException.badRequest("Version label '" + req.versionLabel() + "' already exists for project " + projectId);
        }
        ApiVersionEntity v = new ApiVersionEntity();
        v.setProjectId(projectId);
        v.setVersionLabel(req.versionLabel());
        v.setOpenapiPath(req.openapiPath());
        v.setNotes(req.notes());
        if (req.openapiContent() != null && !req.openapiContent().isBlank()) {
            v.setOpenapiHash(sha256(req.openapiContent()));
        }
        return toDto(apiVersionRepository.save(v));
    }

    public List<ApiVersionDto> listVersions(Long projectId) {
        requireProject(projectId);
        return apiVersionRepository.findByProjectIdOrderByIdDesc(projectId).stream().map(this::toDto).toList();
    }

    @Transactional
    public AnalysisRunDto startRun(Long projectId, StartRunRequest req) {
        requireProject(projectId);
        if (req.apiVersionId() == null) {
            throw ApiException.badRequest("apiVersionId is required to start an analysis run");
        }
        var version = apiVersionRepository.findById(req.apiVersionId())
                .orElseThrow(() -> ApiException.notFound("API version " + req.apiVersionId() + " not found"));
        if (!version.getProjectId().equals(projectId)) {
            throw ApiException.badRequest("API version " + req.apiVersionId() + " does not belong to project " + projectId);
        }
        AnalysisRunEntity run = new AnalysisRunEntity();
        run.setProjectId(projectId);
        run.setApiVersionId(req.apiVersionId());
        run.setStatus("CREATED");
        run.setOpenapiHash(version.getOpenapiHash());
        run.setSourceHash(req.sourceHash());
        run.setAnalyzerVersion(req.analyzerVersion());
        if (req.configuration() != null) {
            run.setConfiguration(req.configuration().toString());
        }
        return toDto(analysisRunRepository.save(run));
    }

    public AnalysisRunDto getRun(Long runId) {
        return analysisRunRepository.findById(runId).map(this::toDto)
                .orElseThrow(() -> ApiException.notFound("Analysis run " + runId + " not found"));
    }

    public List<AnalysisRunDto> listRuns(Long projectId) {
        requireProject(projectId);
        return analysisRunRepository.findByProjectIdOrderByIdDesc(projectId).stream().map(this::toDto).toList();
    }

    @Transactional
    public AnalysisRunDto updateRunStatus(Long runId, String status) {
        String normalized = status.toUpperCase(Locale.ROOT);
        if (!RUN_STATUSES.contains(normalized)) {
            throw ApiException.badRequest("Invalid run status: " + status + ". Allowed: " + RUN_STATUSES);
        }
        AnalysisRunEntity run = analysisRunRepository.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Analysis run " + runId + " not found"));
        run.setStatus(normalized);
        if (normalized.equals("COMPLETED") || normalized.equals("FAILED")) {
            run.setEndedAt(Instant.now());
        }
        return toDto(analysisRunRepository.save(run));
    }

    @Transactional
    public AnalysisRunDto completeRun(Long runId, String resultSummary) {
        AnalysisRunEntity run = analysisRunRepository.findById(runId)
                .orElseThrow(() -> ApiException.notFound("Analysis run " + runId + " not found"));
        run.setStatus("COMPLETED");
        run.setResultSummary(resultSummary);
        run.setEndedAt(Instant.now());
        return toDto(analysisRunRepository.save(run));
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw ApiException.notFound("Project " + projectId + " not found");
        }
    }

    private void apply(ProjectEntity e, String name, String description, String apiName, String baseUrl,
                       String sourcePath, String sourceHash) {
        if (name != null) {
            if (name.isBlank()) throw ApiException.badRequest("Project name must not be blank");
            e.setName(name);
        }
        if (e.getName() == null) throw ApiException.badRequest("Project name must not be blank");
        if (description != null) e.setDescription(description);
        if (apiName != null) e.setApiName(apiName);
        if (baseUrl != null) e.setBaseUrl(baseUrl);
        if (sourcePath != null) e.setSourcePath(sourcePath);
        if (sourceHash != null) e.setSourceHash(sourceHash);
    }

    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private ProjectDto toDto(ProjectEntity e) {
        return new ProjectDto(e.getId(), e.getName(), e.getDescription(), e.getApiName(), e.getBaseUrl(),
                e.getSourcePath(), e.getSourceHash(), e.getCreatedAt().toString());
    }

    private ApiVersionDto toDto(ApiVersionEntity v) {
        return new ApiVersionDto(v.getId(), v.getProjectId(), v.getVersionLabel(), v.getOpenapiPath(),
                v.getOpenapiHash(), v.getNotes(), v.getCreatedAt().toString());
    }

    private AnalysisRunDto toDto(AnalysisRunEntity r) {
        return new AnalysisRunDto(r.getId(), r.getProjectId(), r.getApiVersionId(), r.getStatus(),
                r.getOpenapiHash(), r.getSourceHash(), r.getAnalyzerVersion(), r.getConfiguration(),
                r.getResultSummary(), r.getStartedAt().toString(), r.getEndedAt() == null ? null : r.getEndedAt().toString());
    }
}
