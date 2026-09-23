package com.apixa.project.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "api_versions")
public class ApiVersionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String versionLabel;

    private String openapiPath;
    private String openapiHash;
    private String notes;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getVersionLabel() { return versionLabel; }
    public void setVersionLabel(String versionLabel) { this.versionLabel = versionLabel; }
    public String getOpenapiPath() { return openapiPath; }
    public void setOpenapiPath(String openapiPath) { this.openapiPath = openapiPath; }
    public String getOpenapiHash() { return openapiHash; }
    public void setOpenapiHash(String openapiHash) { this.openapiHash = openapiHash; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
}
