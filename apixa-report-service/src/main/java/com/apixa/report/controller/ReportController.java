package com.apixa.report.controller;

import com.apixa.report.model.GeneratedReport;
import com.apixa.report.model.ReportRequest;
import com.apixa.report.service.ReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step 16 report API (port 8090).
 *
 * <ul>
 *   <li>{@code POST /api/reports/generate} - renders a supplied report snapshot into JSON, HTML and
 *       PDF and returns the report id plus the metadata. This is the only generation route.</li>
 *   <li>{@code GET /api/reports/{reportId}/json|html|pdf} - retrieval of the rendered artifacts.</li>
 * </ul>
 *
 * <p>The service is stateless with respect to analysis: it never calls another APIXA service and never
 * re-runs an analysis step. Retrieval is by the deterministic report id only; no filesystem path is
 * accepted from a client and none is exposed in a response.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;
    private final ObjectMapper mapper;

    public ReportController(ReportService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody ReportRequest request) {
        GeneratedReport report = service.generate(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reportId", report.reportId());
        body.put("metadata", report.metadata());
        body.put("document", report.documentAsMap(mapper));
        body.put("availableFormats", report.availableFormats());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    @GetMapping("/{reportId}/json")
    public ResponseEntity<byte[]> json(@PathVariable String reportId) {
        return serve(reportId, "json", "application/json", ".json");
    }

    @GetMapping("/{reportId}/html")
    public ResponseEntity<byte[]> html(@PathVariable String reportId) {
        return serve(reportId, "html", "text/html;charset=UTF-8", ".html");
    }

    @GetMapping("/{reportId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String reportId) {
        return serve(reportId, "pdf", "application/pdf", ".pdf");
    }

    private ResponseEntity<byte[]> serve(String reportId, String format, String contentType, String suffix) {
        byte[] bytes = service.artifact(reportId, format);
        String safeId = reportId.replaceAll("[^A-Za-z0-9_-]", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename("report-" + safeId + suffix, StandardCharsets.UTF_8).build().toString())
                .body(bytes);
    }
}