package com.apixa.project.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "analysis_runs")
public class AnalysisRunEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long apiVersionId;

    @Column(nullable = false, length = 20)
    private String status; // CREATED, RUNNING, COMPLETED, FAILED

    private String openapiHash;
    private String sourceHash;
    private String analyzerVersion;
    private String configuration;
    private String resultSummary;

    @Column(nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    private Instant endedAt;

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getApiVersionId() { return apiVersionId; }
    public void setApiVersionId(Long apiVersionId) { this.apiVersionId = apiVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOpenapiHash() { return openapiHash; }
    public void setOpenapiHash(String openapiHash) { this.openapiHash = openapiHash; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
    public String getAnalyzerVersion() { return analyzerVersion; }
    public void setAnalyzerVersion(String analyzerVersion) { this.analyzerVersion = analyzerVersion; }
    public String getConfiguration() { return configuration; }
    public void setConfiguration(String configuration) { this.configuration = configuration; }
    public String getResultSummary() { return resultSummary; }
    public void setResultSummary(String resultSummary) { this.resultSummary = resultSummary; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
}
