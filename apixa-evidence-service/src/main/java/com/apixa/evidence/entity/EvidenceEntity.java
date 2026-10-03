package com.apixa.evidence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "evidence")
public class EvidenceEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String evidenceCode;   // EV-<id>

    @Column(nullable = false)
    private String sourceType;     // CONTRACT, SOURCE, MAPPING, SOURCE_SECURITY, CONFORMANCE, STATIC, DYNAMIC

    private String file;
    private Integer lineStart;
    private Integer lineEnd;
    private String jsonPath;
    private String ruleType;
    private String pathPattern;
    private String description;
    private String content;        // full evidence payload (JSON)
    private Long analysisRunId;

    // --- Step 12 additions (additive columns only; existing fields and semantics unchanged) ---
    private String evidenceSetCode;   // groups the CONTRACT -> MAPPING -> SOURCE_SECURITY -> CONFORMANCE chain
    private String httpMethod;        // GET, POST, ...
    private String endpointPath;      // contract or source path, exactly as declared
    private String relatedEvidenceCodes; // comma-separated EV- codes this item links to (Step 12 linkage)

    @Column(nullable = false, updatable = false)
    private long createdAt = System.currentTimeMillis();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEvidenceCode() { return evidenceCode; }
    public void setEvidenceCode(String evidenceCode) { this.evidenceCode = evidenceCode; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getFile() { return file; }
    public void setFile(String file) { this.file = file; }
    public Integer getLineStart() { return lineStart; }
    public void setLineStart(Integer lineStart) { this.lineStart = lineStart; }
    public Integer getLineEnd() { return lineEnd; }
    public void setLineEnd(Integer lineEnd) { this.lineEnd = lineEnd; }
    public String getJsonPath() { return jsonPath; }
    public void setJsonPath(String jsonPath) { this.jsonPath = jsonPath; }
    public String getRuleType() { return ruleType; }
    public void setRuleType(String ruleType) { this.ruleType = ruleType; }
    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getEvidenceSetCode() { return evidenceSetCode; }
    public void setEvidenceSetCode(String v) { this.evidenceSetCode = v; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String v) { this.httpMethod = v; }
    public String getEndpointPath() { return endpointPath; }
    public void setEndpointPath(String v) { this.endpointPath = v; }
    public String getRelatedEvidenceCodes() { return relatedEvidenceCodes; }
    public void setRelatedEvidenceCodes(String v) { this.relatedEvidenceCodes = v; }

    public Long getAnalysisRunId() { return analysisRunId; }
    public void setAnalysisRunId(Long analysisRunId) { this.analysisRunId = analysisRunId; }
    public long getCreatedAt() { return createdAt; }
}
