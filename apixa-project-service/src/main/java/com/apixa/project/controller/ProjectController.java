package com.apixa.project.controller;

import com.apixa.project.model.ProjectDtos.*;
import com.apixa.project.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) { this.projectService = projectService; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@Valid @RequestBody CreateProjectRequest request) {
        return projectService.createProject(request);
    }

    @GetMapping
    public List<ProjectDto> list() { return projectService.listProjects(); }

    @GetMapping("/{id}")
    public ProjectDto get(@PathVariable Long id) { return projectService.getProject(id); }

    @PutMapping("/{id}")
    public ProjectDto update(@PathVariable Long id, @RequestBody UpdateProjectRequest request) {
        return projectService.updateProject(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { projectService.deleteProject(id); }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiVersionDto addVersion(@PathVariable Long id, @Valid @RequestBody CreateVersionRequest request) {
        return projectService.addVersion(id, request);
    }

    @GetMapping("/{id}/versions")
    public List<ApiVersionDto> versions(@PathVariable Long id) { return projectService.listVersions(id); }

    @PostMapping("/{id}/analysis-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisRunDto startRun(@PathVariable Long id, @RequestBody StartRunRequest request) {
        return projectService.startRun(id, request);
    }

    @GetMapping("/{id}/analysis-runs")
    public List<AnalysisRunDto> runs(@PathVariable Long id) { return projectService.listRuns(id); }

    @GetMapping("/analysis-runs/{runId}")
    public AnalysisRunDto run(@PathVariable Long runId) { return projectService.getRun(runId); }

    @PostMapping("/analysis-runs/{runId}/status")
    public AnalysisRunDto updateRunStatus(@PathVariable Long runId, @Valid @RequestBody UpdateRunStatusRequest request) {
        return projectService.updateRunStatus(runId, request.status());
    }

    @PostMapping("/analysis-runs/{runId}/complete")
    public AnalysisRunDto completeRun(@PathVariable Long runId, @RequestBody(required = false) Map<String, String> body) {
        return projectService.completeRun(runId, body == null ? null : body.get("resultSummary"));
    }
}
