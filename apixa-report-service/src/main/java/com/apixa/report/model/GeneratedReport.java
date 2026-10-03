package com.apixa.report.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * A generated report: the JSON document itself plus the small handle the API returns.
 *
 * <p>{@code document} is the canonical report model rendered as JSON. The HTML and PDF renderings are
 * produced from exactly this model, so the three formats can never disagree.
 *
 * <p>{@code reportId} is <b>deterministic</b>: it is derived from the supplied content, not from a
 * random UUID, so the same input always yields the same report id and the same file names. Only
 * {@code generatedAt} varies between two runs of identical input.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeneratedReport(
        String reportId,
        ReportMetadata metadata,
        JsonNode document,
        List<String> availableFormats) {

    /** Explicitly generated metadata; everything else is a faithful representation of the input. */
    public record ReportMetadata(
            String reportId,
            String title,
            String generatedAt,
            String generator,
            String schemaVersion,
            Long projectId,
            String versionLabel,
            Long analysisRunId,
            String contractId,
            String evidenceSetId,
            String benchmarkId,
            List<String> suppliedSections,
            List<String> notProvidedSections,
            String note) {}

    /**
     * The document as plain maps. The renderers read the {@link JsonNode} directly, but an API response
     * must not expose it as a raw node: some Jackson configurations serialize a node as its internal
     * properties ("array", "containerNode", ...) instead of its content.
     */
    public Map<String, Object> documentAsMap(ObjectMapper mapper) {
        return mapper.convertValue(document, Map.class);
    }
}