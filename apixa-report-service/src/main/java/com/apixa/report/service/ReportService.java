package com.apixa.report.service;

import com.apixa.report.model.GeneratedReport;
import com.apixa.report.model.ReportRequest;
import com.apixa.report.render.HtmlReportRenderer;
import com.apixa.report.render.PdfReportRenderer;
import com.apixa.report.render.ReportSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 16 report generation.
 *
 * <p><b>Presentation only.</b> This service receives results that earlier steps already produced and
 * renders them as JSON, HTML and PDF. It never calls another APIXA service, never re-parses an OpenAPI
 * document, never scans source, never maps, compares, verifies, runs a benchmark or computes a metric.
 * Every value in the report comes from the request; summaries are counts over the supplied data, never
 * new findings.
 *
 * <p><b>Storage.</b> Generated artifacts are held in memory and, when
 * {@code apixa.report.write-files=true}, also written to a controlled output directory
 * ({@code apixa.report.output-dir}, default {@code ./data/reports}) with deterministic names
 * {@code report-{reportId}.json|html|pdf}. Clients can never choose an output path.
 *
 * <p><b>Determinism.</b> The report id is a SHA-256 digest of the canonical request, so identical input
 * always produces the same id, the same section ordering and byte-identical JSON/HTML (apart from
 * {@code generatedAt}, which is the only intentionally variable field).
 */
@Service
public class ReportService {

    /** Marker used wherever a section was not supplied. Never an empty success. */
    public static final String NOT_PROVIDED = "notProvided";

    private static final String SCHEMA_VERSION = "apixa-report-1";
    private static final List<String> SECTION_ORDER = List.of(
            "project", "apiVersion", "analysisRun", "contract", "sourceAnalysis", "mappings",
            "conformance", "evidence", "runtime", "impact", "benchmark", "limitations");

    private final ObjectMapper mapper;
    private final ReportSanitizer sanitizer;
    private final HtmlReportRenderer htmlRenderer;
    private final PdfReportRenderer pdfRenderer;

    /** Generated artifacts, keyed by the deterministic report id. */
    private final Map<String, GeneratedReport> reports = new ConcurrentHashMap<>();
    private final Map<String, Map<String, byte[]>> artifacts = new ConcurrentHashMap<>();

    @Value("${apixa.report.output-dir:./data/reports}")
    private String outputDir;

    @Value("${apixa.report.write-files:false}")
    private boolean writeFiles;

    public ReportService(ObjectMapper mapper, ReportSanitizer sanitizer, HtmlReportRenderer htmlRenderer,
                         PdfReportRenderer pdfRenderer) {
        this.mapper = mapper;
        this.sanitizer = sanitizer;
        this.htmlRenderer = htmlRenderer;
        this.pdfRenderer = pdfRenderer;
    }

    /** Generates all three renderings from one supplied request. */
    public GeneratedReport generate(ReportRequest request) {
        if (request == null) throw com.apixa.common.error.ApiException.badRequest("Report request body is required");
        if (request.project() == null && request.apiVersion() == null && request.analysisRun() == null
                && request.contract() == null && request.sourceAnalysis() == null
                && isEmpty(request.mappings()) && isEmpty(request.conformance())
                && request.evidence() == null && isEmpty(request.runtime())
                && request.impact() == null && request.benchmark() == null) {
            throw com.apixa.common.error.ApiException.badRequest(
                    "Report request contains no reportable content: supply at least one of project,"
                            + " apiVersion, analysisRun, contract, sourceAnalysis, mappings, conformance,"
                            + " evidence, runtime, impact or benchmark");
        }

        ReportRequest sanitized = sanitizer.sanitize(request);
        ObjectNode document = buildDocument(sanitized);
        String reportId = reportId(document);
        GeneratedReport report = new GeneratedReport(reportId, metadata(sanitized, reportId), document,
                List.of("JSON", "HTML", "PDF"));

        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("json", write(document));
        files.put("html", htmlRenderer.render(report).getBytes(StandardCharsets.UTF_8));
        files.put("pdf", pdfRenderer.render(report));

        reports.put(reportId, report);
        artifacts.put(reportId, files);
        if (writeFiles) writeToDisk(reportId, files);
        return report;
    }

    public GeneratedReport report(String reportId) {
        GeneratedReport report = reports.get(reportId);
        if (report == null) {
            throw com.apixa.common.error.ApiException.notFound("Report not found: " + reportId);
        }
        return report;
    }

    /** Returns one rendered artifact; {@code format} is json, html or pdf. */
    public byte[] artifact(String reportId, String format) {
        report(reportId);
        byte[] bytes = artifacts.get(reportId).get(format.toLowerCase(java.util.Locale.ROOT));
        if (bytes == null) {
            throw com.apixa.common.error.ApiException.notFound("Report format not available: " + format);
        }
        return bytes;
    }

    public boolean known(String reportId) {
        return reports.containsKey(reportId);
    }
    /**
     * Builds the canonical report document. Sections appear in a fixed order and are never omitted: a
     * section that was not supplied is rendered as {@link #NOT_PROVIDED}, so a missing result can never
     * be mistaken for a clean result.
     *
     * <p>Per-section summaries are counts over the supplied data. They describe the input; they never
     * derive a new verdict.
     */
    private ObjectNode buildDocument(ReportRequest r) {
        ObjectNode doc = mapper.createObjectNode();
        doc.put("schemaVersion", SCHEMA_VERSION);
        section(doc, "project", r.project());
        section(doc, "apiVersion", r.apiVersion());
        section(doc, "analysisRun", r.analysisRun());

        withSummary(doc, "contract", r.contract(), r.contract() == null ? null : contractSummary(r.contract()));
        withSummary(doc, "sourceAnalysis", r.sourceAnalysis(),
                r.sourceAnalysis() == null ? null : sourceSummary(r.sourceAnalysis()));
        withSummary(doc, "mappings", r.mappings(), isEmpty(r.mappings()) ? null : mappingSummary(r.mappings()));
        withSummary(doc, "conformance", r.conformance(),
                isEmpty(r.conformance()) ? null : conformanceSummary(r.conformance()));
        withSummary(doc, "evidence", r.evidence(), r.evidence() == null ? null : evidenceSummary(r.evidence()));
        withSummary(doc, "runtime", r.runtime(), isEmpty(r.runtime()) ? null : runtimeSummary(r.runtime()));
        withSummary(doc, "impact", r.impact(), r.impact() == null ? null : impactSummary(r.impact()));
        withSummary(doc, "benchmark", r.benchmark(), r.benchmark() == null ? null : benchmarkSummary(r.benchmark()));
        section(doc, "limitations", r.limitations());
        return doc;
    }

    private void withSummary(ObjectNode doc, String name, Object value, ObjectNode summary) {
        ObjectNode node = section(doc, name, value);
        if (summary != null) node.set("summary", summary);
    }

    /**
     * Writes one section in the fixed order. Every section carries {@code provided}, so an absent
     * section is explicit rather than silently missing.
     */
    private ObjectNode section(ObjectNode doc, String name, Object value) {
        ObjectNode node = mapper.createObjectNode();
        if (value == null) {
            node.put("provided", false);
            node.put("status", NOT_PROVIDED);
        } else if (value instanceof List<?> list && list.isEmpty()) {
            node.put("provided", true);
            node.put("status", "supplied_empty");
            node.set(name, mapper.valueToTree(list));
        } else {
            node.put("provided", true);
            node.put("status", "supplied");
            node.set(name, mapper.valueToTree(value));
        }
        doc.set(name, node);
        return node;
    }

    private ObjectNode contractSummary(ReportRequest.ContractSection c) {
        ObjectNode s = mapper.createObjectNode();
        s.put("contractId", c.contractId());
        s.put("contractStatus", c.status());
        s.put("openapiVersion", c.openapiVersion());
        s.put("endpointCount", c.endpointCount() == null ? count(c.endpoints()) : c.endpointCount());
        s.put("securitySchemeCount", c.securitySchemeCount());
        return s;
    }

    private ObjectNode sourceSummary(ReportRequest.SourceAnalysisSection s) {
        ObjectNode n = mapper.createObjectNode();
        n.put("analysisType", s.analysisType());
        n.put("status", s.status());
        n.put("analyzedFiles", s.analyzedFiles());
        n.put("endpointCount", s.endpointCount() == null ? count(s.endpoints()) : s.endpointCount());
        n.put("securityRuleCount", s.securityRuleCount() == null ? count(s.securityRules()) : s.securityRuleCount());
        List<ReportRequest.SourceSecurityRuleView> rules = s.securityRules() == null ? List.of() : s.securityRules();
        ObjectNode notes = n.putObject("notTranslatedRules");
        notes.put("unresolved", rules.stream().filter(x -> Boolean.TRUE.equals(x.unresolved())).count());
        notes.put("complexExpression", rules.stream().filter(x -> Boolean.TRUE.equals(x.complex())).count());
        return n;
    }
    private ObjectNode mappingSummary(List<ReportRequest.MappingView> mappings) {
        ObjectNode s = mapper.createObjectNode();
        s.put("total", mappings.size());
        s.set("byStatus", tally(mappings.stream().map(ReportRequest.MappingView::status).toList()));
        return s;
    }

    /** Counts the supplied verdicts verbatim; it does not create or alter any verdict. */
    private ObjectNode conformanceSummary(List<ReportRequest.ConformanceView> results) {
        ObjectNode s = mapper.createObjectNode();
        s.put("total", results.size());
        s.set("byConformanceStatus", tally(results.stream()
                .map(ReportRequest.ConformanceView::conformanceStatus).toList()));
        s.set("byMappingStatus", tally(results.stream()
                .map(ReportRequest.ConformanceView::mappingStatus).toList()));
        return s;
    }

    private ObjectNode evidenceSummary(ReportRequest.EvidenceSection e) {
        ObjectNode s = mapper.createObjectNode();
        s.put("evidenceSetId", e.evidenceSetId());
        s.put("itemCount", e.itemCount() == null ? count(e.items()) : e.itemCount());
        if (e.items() != null) {
            s.set("bySourceType", tally(e.items().stream()
                    .map(ReportRequest.EvidenceItemView::sourceType).toList()));
        }
        return s;
    }

    /** Runtime observations are counted as observations; no conformance verdict is derived here. */
    private ObjectNode runtimeSummary(List<ReportRequest.RuntimeView> runtime) {
        ObjectNode s = mapper.createObjectNode();
        s.put("total", runtime.size());
        s.set("byExecutionStatus", tally(runtime.stream()
                .map(ReportRequest.RuntimeView::executionStatus).toList()));
        s.set("byObservedStatusCode", tally(runtime.stream()
                .map(r -> r.observedStatusCode() == null ? "none" : String.valueOf(r.observedStatusCode()))
                .toList()));
        s.put("note", "Runtime values are observations. No conformance verdict is derived from them.");
        return s;
    }

    /** Impact categories are counted as supplied facts; nothing is ranked or scored. */
    private ObjectNode impactSummary(ReportRequest.ImpactSection i) {
        ObjectNode s = mapper.createObjectNode();
        s.put("totalImpacts", i.impactCount() == null ? count(i.impacts()) : i.impactCount());
        s.set("byCategory", tally(i.impacts() == null ? List.of()
                : i.impacts().stream().map(ReportRequest.ImpactRecordView::category).toList()));
        s.put("note", "Impact records are factual differences between two supplied versions;"
                + " no severity or ranking is applied.");
        return s;
    }
    private ObjectNode benchmarkSummary(ReportRequest.BenchmarkSection b) {
        ObjectNode s = mapper.createObjectNode();
        s.put("benchmarkId", b.benchmarkId());
        s.put("status", b.status());
        s.put("totalCases", b.totalCases());
        s.put("executedCases", b.executedCases());
        s.put("skippedCases", b.skippedCases());
        s.put("passedCases", b.passedCases());
        s.put("failedCases", b.failedCases());
        s.put("accuracy", b.accuracy());
        if (b.conformanceMetrics() != null) {
            ObjectNode c = s.putObject("conformanceMetrics");
            c.put("accuracy", b.conformanceMetrics().accuracy());
            c.put("precision", b.conformanceMetrics().precision());
            c.put("recall", b.conformanceMetrics().recall());
            c.put("f1", b.conformanceMetrics().f1());
            c.put("positiveClass", b.conformanceMetrics().positiveClass());
            c.set("confusionMatrix", mapper.valueToTree(b.conformanceMetrics().confusionMatrix()));
        }
        s.set("mappingMetrics", mapper.valueToTree(b.mappingMetrics()));
        s.set("impactMetrics", mapper.valueToTree(b.impactMetrics()));
        s.set("runtimeMetrics", mapper.valueToTree(b.runtimeMetrics()));
        s.set("mutationMetrics", mapper.valueToTree(b.mutationMetrics()));
        s.set("determinism", mapper.valueToTree(b.determinism()));
        s.put("interpretation", b.interpretation());
        return s;
    }

    /** Sorted count map, so JSON output never depends on hash iteration order. */
    private com.fasterxml.jackson.databind.node.ObjectNode tally(List<String> values) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (String v : values) counts.merge(v == null ? NOT_PROVIDED : v, 1, Integer::sum);
        return mapper.valueToTree(counts);
    }

    private int count(List<?> list) { return list == null ? 0 : list.size(); }
    private boolean isEmpty(List<?> list) { return list == null || list.isEmpty(); }
    private GeneratedReport.ReportMetadata metadata(ReportRequest r, String reportId) {
        List<String> supplied = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String name : SECTION_ORDER) {
            Object value = valueOf(r, name);
            if (value == null || (value instanceof List<?> l && l.isEmpty())) missing.add(name);
            else supplied.add(name);
        }
        return new GeneratedReport.ReportMetadata(
                reportId,
                title(r),
                Instant.now().toString(),
                "apixa-report-service",
                SCHEMA_VERSION,
                r.project() == null ? null : r.project().id(),
                r.apiVersion() == null ? null : r.apiVersion().versionLabel(),
                r.analysisRun() == null ? null : r.analysisRun().id(),
                r.contract() == null ? null : r.contract().contractId(),
                r.evidence() == null ? null : r.evidence().evidenceSetId(),
                r.benchmark() == null ? null : r.benchmark().benchmarkId(),
                supplied, missing,
                "This report renders results produced by earlier APIXA steps. It does not re-run any"
                        + " analysis and does not modify any supplied value.");
    }

    private String title(ReportRequest r) {
        String project = r.project() == null ? "APIXA" : r.project().name();
        String version = r.apiVersion() == null ? "" : " " + r.apiVersion().versionLabel();
        return project + version + " - APIXA Security Contract Conformance Report";
    }

    private Object valueOf(ReportRequest r, String name) {
        return switch (name) {
            case "project" -> r.project();
            case "apiVersion" -> r.apiVersion();
            case "analysisRun" -> r.analysisRun();
            case "contract" -> r.contract();
            case "sourceAnalysis" -> r.sourceAnalysis();
            case "mappings" -> r.mappings();
            case "conformance" -> r.conformance();
            case "evidence" -> r.evidence();
            case "runtime" -> r.runtime();
            case "impact" -> r.impact();
            case "benchmark" -> r.benchmark();
            case "limitations" -> r.limitations();
            default -> null;
        };
    }

    /**
     * Deterministic report id: a SHA-256 digest of the canonical document. Identical input therefore
     * yields the same report id and the same file names, which is what makes repeated generation
     * reproducible apart from {@code generatedAt}.
     */
    private String reportId(JsonNode document) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(document));
            return "RPT-" + HexFormat.of().formatHex(digest).substring(0, 16)
                    .toUpperCase(java.util.Locale.ROOT);
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive a report id", e);
        }
    }

    private byte[] write(JsonNode document) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize the report", e);
        }
    }

    /** Writes the artifacts into the controlled output directory using deterministic names. */
    private void writeToDisk(String reportId, Map<String, byte[]> files) {
        try {
            Path dir = Path.of(outputDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("report-" + reportId + ".json"),
                    new String(files.get("json"), StandardCharsets.UTF_8));
            Files.write(dir.resolve("report-" + reportId + ".html"), files.get("html"));
            Files.write(dir.resolve("report-" + reportId + ".pdf"), files.get("pdf"));
        } catch (Exception e) {
            // File output is a convenience; a failure must never fail the report itself.
            System.err.println("Report file output skipped: " + e.getMessage());
        }
    }
}