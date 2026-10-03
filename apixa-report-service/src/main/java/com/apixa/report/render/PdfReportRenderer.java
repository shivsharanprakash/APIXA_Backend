package com.apixa.report.render;

import com.apixa.report.model.GeneratedReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfDocument;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Step 16 PDF rendering: a real, programmatically laid out PDF (not a screenshot of the HTML).
 *
 * <p>Uses the OpenPDF library that this module already declared. The layout engine is OpenPDF itself,
 * so text is measured, wrapped and paginated by the library: cells grow with their content, which is
 * what prevents clipped or overlapping text.
 *
 * <p>Only ASCII-safe glyphs are used (the built-in Helvetica family, ASCII hyphen instead of a
 * typographic dash), so no broken characters can appear regardless of the installed fonts.
 *
 * <p>Every page carries the report title and a "Page n of m" footer.
 */
@Component
public class PdfReportRenderer {

    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
    private static final Font H3 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9.5f);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f);
    private static final Font MONO = FontFactory.getFont(FontFactory.COURIER, 8.5f);
    private static final Font MONO_BOLD = FontFactory.getFont(FontFactory.COURIER_BOLD, 8.5f);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8f);
    private static final Font HEADER_CELL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f);

    /**
     * Renders the report to PDF.
     *
     * <p>The running header and the page number are placed in the page margins through OpenPDF's
     * {@code HeaderFooter} support rather than into the content flow: a footer written into the flow can
     * overflow the text area, which makes OpenPDF open a new page, re-enter the page event and recurse
     * (observed as a StackOverflowError). Margin placement cannot reflow the body.
     *
     * <p>This build exposes no total-page accessor, so the document is laid out twice: the first pass
     * counts the pages and the second pass renders the same content with the total stated on the cover.
     */
    public byte[] render(GeneratedReport report) {
        byte[] first = renderOnce(report, 0);
        int total = lastPageCount;
        return total <= 0 ? first : renderOnce(report, total);
    }

    /** Page count of the last completed pass. */
    private int lastPageCount;

    /**
     * The built-in Helvetica base font. It is created once and cached because creating it performs I/O
     * and would otherwise be repeated on every page.
     */
    private static final BaseFont HELVETICA = createHelvetica();

    private static BaseFont createHelvetica() {
        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not load the built-in Helvetica font", e);
        }
    }

    private BaseFont baseFont() { return HELVETICA; }

    private byte[] renderOnce(GeneratedReport report, int totalPages) {
        lastPageCount = 0;
        ByteArrayOutputStream out = new ByteArrayOutputStream(65536);
        Document document = new Document(PageSize.A4, 40, 40, 52, 56);
        PdfWriter writer = PdfWriter.getInstance(document, out);

        // Header and page number are drawn straight onto the page canvas instead of being added to the
        // content flow: flow content can overflow the text area, which makes OpenPDF open a new page,
        // re-enter the page event and recurse. Canvas text cannot reflow the body at all.
        int[] page = { 0 };
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, com.lowagie.text.Document doc) {
                page[0]++;
                PdfContentByte cb = w.getDirectContent();
                Rectangle size = doc.getPageSize();
                float margin = doc.leftMargin();
                cb.saveState();
                cb.beginText();
                cb.setFontAndSize(baseFont(), 8f);
                cb.setTextMatrix(margin, size.getHeight() - 40);
                cb.showText(safe(report.metadata().title()));
                cb.endText();
                cb.beginText();
                String footer = totalPages > 0
                        ? "Page " + page[0] + " of " + totalPages
                        : "Page " + page[0];
                // Centred horizontally: the page box is symmetric, so the left margin offsets the origin.
                cb.setTextMatrix(margin, 34);
                cb.showText(footer);
                cb.endText();
                cb.restoreState();
            }
        });
        document.open();
        try {
            JsonNode doc = report.document();
            cover(document, report, totalPages);
            overview(document, doc);
            identity(document, doc);
            contract(document, doc);
            source(document, doc);
            mappings(document, doc);
            conformance(document, doc);
            evidence(document, doc);
            runtime(document, doc);
            impact(document, doc);
            benchmark(document, doc);
            limitations(document, doc);
            closing(document, report);
        } finally {
            document.close();
        }
        lastPageCount = page[0];
        return out.toByteArray();
    }
    private void cover(Document d, GeneratedReport report, int totalPages) {
        GeneratedReport.ReportMetadata m = report.metadata();
        d.add(heading(m.title(), H1, 10));
        d.add(para("APIXA security contract conformance report", SMALL, 4));
        PdfPTable meta = grid(2);
        meta.addCell(cell("Report id", m.reportId(), HEADER_CELL, MONO));
        meta.addCell(cell("Generated at", m.generatedAt(), HEADER_CELL, MONO));
        meta.addCell(cell("Generated by", m.generator(), HEADER_CELL, BODY));
        meta.addCell(cell("Schema version", m.schemaVersion(), HEADER_CELL, MONO));
        meta.addCell(cell("Project id", m.projectId(), HEADER_CELL, MONO));
        meta.addCell(cell("API version", m.versionLabel(), HEADER_CELL, BODY));
        meta.addCell(cell("Analysis run id", m.analysisRunId(), HEADER_CELL, MONO));
        meta.addCell(cell("Contract id", m.contractId(), HEADER_CELL, MONO));
        meta.addCell(cell("Evidence set id", m.evidenceSetId(), HEADER_CELL, MONO));
        meta.addCell(cell("Benchmark id", m.benchmarkId(), HEADER_CELL, MONO));
        meta.addCell(cell("Total pages", totalPages > 0 ? String.valueOf(totalPages) : "counting",
                HEADER_CELL, BODY));
        d.add(meta);
        d.add(para(m.note(), SMALL, 8));
    }

    private void overview(Document d, JsonNode doc) {
        d.add(heading("1. Report Overview", H2, 12));
        d.add(para("Conformance verdicts are shown exactly as they were supplied; this report does not"
                + " compute, re-score or re-order them.", BODY, 6));
        JsonNode summary = doc.path("conformance").path("summary");
        if (summary.isMissingNode()) {
            d.add(para("Conformance results were not provided for this report.", BODY, 4));
            return;
        }
        d.add(heading("Conformance verdicts (as supplied)", H3, 8));
        d.add(counts(summary.path("byConformanceStatus")));
    }

    private void identity(Document d, JsonNode doc) {
        d.add(heading("2. Project / API Version / Analysis Run", H2, 12));
        kv(d, "Project", doc.path("project"), "project");
        kv(d, "API version", doc.path("apiVersion"), "apiVersion");
        kv(d, "Analysis run", doc.path("analysisRun"), "analysisRun");
    }

    private void contract(Document d, JsonNode doc) {
        d.add(heading("3. Contract Summary", H2, 12));
        JsonNode node = doc.path("contract");
        if (!provided(node)) { notProvided(d, "Contract"); return; }
        JsonNode c = node.path("contract");
        PdfPTable t = grid(2);
        t.addCell(cell("Contract id", text(c, "contractId"), HEADER_CELL, MONO));
        t.addCell(cell("Status", text(c, "status"), HEADER_CELL, BODY));
        t.addCell(cell("OpenAPI version", text(c, "openapiVersion"), HEADER_CELL, MONO));
        t.addCell(cell("Source file", text(c, "sourceFile"), HEADER_CELL, MONO));
        t.addCell(cell("Title", text(c, "title"), HEADER_CELL, BODY));
        t.addCell(cell("Endpoints", text(node.path("summary"), "endpointCount"), HEADER_CELL, BODY));
        t.addCell(cell("Security schemes", text(node.path("summary"), "securitySchemeCount"), HEADER_CELL, BODY));
        d.add(t);
        JsonNode endpoints = c.path("endpoints");
        if (endpoints.isArray() && !endpoints.isEmpty()) {
            d.add(heading("Contract endpoints", H3, 8));
            PdfPTable t2 = table(5, "Method", "Path", "Operation", "Declared security", "JSON path");
            for (JsonNode e : endpoints) {
                t2.addCell(value(text(e, "method"), MONO));
                t2.addCell(value(text(e, "path"), MONO));
                t2.addCell(value(text(e, "operationId"), MONO));
                t2.addCell(value(policy(e.path("expectedSecurity")), BODY));
                t2.addCell(value(text(e, "jsonPath"), MONO));
            }
            d.add(t2);
        }
    }
    private void source(Document d, JsonNode doc) {
        d.add(heading("4. Source Analysis", H2, 12));
        JsonNode node = doc.path("sourceAnalysis");
        if (!provided(node)) { notProvided(d, "Source analysis"); return; }
        JsonNode s = node.path("sourceAnalysis");
        JsonNode sum = node.path("summary");
        PdfPTable t = grid(2);
        t.addCell(cell("Analysis type", text(s, "analysisType"), HEADER_CELL, BODY));
        t.addCell(cell("Status", text(s, "status"), HEADER_CELL, BODY));
        t.addCell(cell("Analyzed files", text(sum, "analyzedFiles"), HEADER_CELL, BODY));
        t.addCell(cell("Endpoints", text(sum, "endpointCount"), HEADER_CELL, BODY));
        t.addCell(cell("Security rules", text(sum, "securityRuleCount"), HEADER_CELL, BODY));
        t.addCell(cell("Unresolved rules", text(sum.path("notTranslatedRules"), "unresolved"), HEADER_CELL, BODY));
        t.addCell(cell("Complex expressions",
                text(sum.path("notTranslatedRules"), "complexExpression"), HEADER_CELL, BODY));
        d.add(t);
        JsonNode rules = s.path("securityRules");
        if (rules.isArray() && !rules.isEmpty()) {
            d.add(heading("Source security rules (Step 9)", H3, 8));
            PdfPTable t2 = table(6, "Scope", "Operator", "Path pattern", "Roles", "File", "Line");
            t2.setWidths(new float[] { 20f, 15f, 20f, 12f, 20f, 13f });
            for (JsonNode r : rules) {
                t2.addCell(value(text(r, "scope"), BODY));
                t2.addCell(value(text(r, "operator"), MONO));
                t2.addCell(value(text(r, "pathPattern"), MONO));
                t2.addCell(value(list(r, "roles"), MONO));
                t2.addCell(value(text(r, "file"), MONO));
                t2.addCell(value(text(r, "lineStart"), BODY));
            }
            d.add(t2);
        }
    }

    private void mappings(Document d, JsonNode doc) {
        d.add(heading("5. Endpoint Mapping", H2, 12));
        JsonNode node = doc.path("mappings");
        if (!provided(node)) { notProvided(d, "Endpoint mapping"); return; }
        d.add(counts(node.path("summary").path("byStatus")));
        JsonNode items = node.path("mappings");
        if (items.isArray() && !items.isEmpty()) {
            PdfPTable t = table(5, "Contract endpoint", "Status", "Confidence", "Implementation", "Reason");
            t.setWidths(new float[] { 22f, 13f, 11f, 29f, 25f });
            for (JsonNode m : items) {
                t.addCell(value(join(text(m, "method"), text(m, "path")), MONO));
                t.addCell(value(text(m, "status"), MONO_BOLD));
                t.addCell(value(text(m, "confidence"), MONO));
                String impl = blank(text(m, "implementationClass"))
                        ? "" : text(m, "implementationClass") + "#" + text(m, "implementationMethod");
                t.addCell(value(impl + " " + text(m, "implementationPath"), MONO));
                t.addCell(value(text(m, "reason"), SMALL));
            }
            d.add(t);
        }
    }
    private void conformance(Document d, JsonNode doc) {
        d.add(heading("6. Security Conformance", H2, 12));
        JsonNode node = doc.path("conformance");
        if (!provided(node)) { notProvided(d, "Conformance results"); return; }
        JsonNode items = node.path("conformance");
        for (JsonNode c : items) {
            d.add(heading(text(c, "contractMethod") + " " + text(c, "contractPath"), H3, 8));
            PdfPTable t = grid(2);
            t.addCell(cell("Mapping", text(c, "mappingStatus"), HEADER_CELL, MONO_BOLD));
            t.addCell(cell("Expected security", policy(c.path("expectedSecurity")), HEADER_CELL, BODY));
            t.addCell(cell("Implemented security", policy(c.path("implementedSecurity")), HEADER_CELL, BODY));
            t.addCell(cell("Conformance", text(c, "conformanceStatus"), HEADER_CELL, MONO_BOLD));
            t.addCell(cell("Reason", text(c, "reason"), HEADER_CELL, BODY));
            JsonNode applied = c.path("appliedRule");
            if (!applied.isMissingNode()) {
                t.addCell(cell("Applied rule", text(applied, "operator") + " " + text(applied, "pathPattern")
                        + " (" + text(applied, "file") + ":" + text(applied, "lineStart") + ")", HEADER_CELL, MONO));
            }
            JsonNode codes = c.path("evidenceCodes");
            if (codes.isArray() && !codes.isEmpty()) {
                t.addCell(cell("Evidence", String.join(", ", toList(codes)), HEADER_CELL, MONO));
            }
            t.setKeepTogether(true);
            d.add(t);
        }
    }

    private void evidence(Document d, JsonNode doc) {
        d.add(heading("7. Evidence Traceability", H2, 12));
        JsonNode node = doc.path("evidence");
        if (!provided(node)) { notProvided(d, "Evidence"); return; }
        JsonNode e = node.path("evidence");
        d.add(para("Contract -> Mapping -> Source endpoint -> Source security -> Conformance", MONO, 6));
        PdfPTable t = grid(2);
        t.addCell(cell("Evidence set", text(e, "evidenceSetId"), HEADER_CELL, MONO));
        t.addCell(cell("Items", text(node.path("summary"), "itemCount"), HEADER_CELL, BODY));
        d.add(t);
        JsonNode items = e.path("items");
        if (items.isArray() && !items.isEmpty()) {
            PdfPTable t2 = table(7, "Code", "Type", "Endpoint", "File / line", "Rule", "Path pattern", "Related");
            t2.setWidths(new float[] { 14f, 16f, 18f, 18f, 12f, 15f, 15f });
            for (JsonNode i : items) {
                t2.addCell(value(text(i, "evidenceCode"), MONO));
                t2.addCell(value(text(i, "sourceType"), MONO));
                t2.addCell(value(join(text(i, "httpMethod"), text(i, "endpointPath")), MONO));
                t2.addCell(value(fileLine(i), MONO));
                t2.addCell(value(text(i, "ruleType"), MONO));
                t2.addCell(value(text(i, "pathPattern"), MONO));
                t2.addCell(value(list(i, "relatedEvidenceCodes"), MONO));
            }
            d.add(t2);
        }
    }
    private void runtime(Document d, JsonNode doc) {
        d.add(heading("8. Runtime Verification", H2, 12));
        JsonNode node = doc.path("runtime");
        if (!provided(node)) { notProvided(d, "Runtime verification"); return; }
        d.add(para("Runtime values are observations of an executed request. No conformance verdict is"
                + " derived from them.", BODY, 6));
        JsonNode items = node.path("runtime");
        if (items.isArray() && !items.isEmpty()) {
            PdfPTable t = table(5, "Request", "Observed status", "Execution status", "Error", "Observed at");
            for (JsonNode r : items) {
                t.addCell(value(text(r, "method") + " " + text(r, "url"), MONO));
                t.addCell(value(text(r, "observedStatusCode"), MONO_BOLD));
                t.addCell(value(text(r, "executionStatus"), MONO));
                t.addCell(value(text(r, "errorType"), MONO));
                t.addCell(value(text(r, "observedAt"), SMALL));
            }
            d.add(t);
        }
    }

    private void impact(Document d, JsonNode doc) {
        d.add(heading("9. Change Impact (V1 to V2)", H2, 12));
        JsonNode node = doc.path("impact");
        if (!provided(node)) { notProvided(d, "Change impact"); return; }
        JsonNode i = node.path("impact");
        PdfPTable t = grid(2);
        t.addCell(cell("V1 version", text(i, "v1VersionId"), HEADER_CELL, BODY));
        t.addCell(cell("V2 version", text(i, "v2VersionId"), HEADER_CELL, BODY));
        t.addCell(cell("Impact records", text(node.path("summary"), "totalImpacts"), HEADER_CELL, BODY));
        d.add(t);
        JsonNode items = i.path("impacts");
        if (items.isArray() && !items.isEmpty()) {
            PdfPTable t2 = table(6, "Category", "Endpoint", "V1", "V2", "Details", "Evidence");
            for (JsonNode r : items) {
                String v1 = firstNonBlank(text(r, "v1Summary"), text(r, "v1ConformanceStatus"),
                        text(r, "v1Path"), text(r, "v1ObservedStatusCode"), "");
                String v2 = firstNonBlank(text(r, "v2Summary"), text(r, "v2ConformanceStatus"),
                        text(r, "v2Path"), text(r, "v2ObservedStatusCode"), "");
                t2.addCell(value(text(r, "category"), MONO_BOLD));
                t2.addCell(value(join(text(r, "method"), text(r, "path")), MONO));
                t2.addCell(value(v1, MONO));
                t2.addCell(value(v2, MONO));
                t2.addCell(value(text(r, "details"), SMALL));
                t2.addCell(value(list(r, "v1EvidenceCodes") + " " + list(r, "v2EvidenceCodes"), MONO));
            }
            d.add(t2);
            d.add(para(text(node.path("summary"), "note"), SMALL, 6));
        }
    }
    private void benchmark(Document d, JsonNode doc) {
        d.add(heading("10. Benchmark", H2, 12));
        JsonNode node = doc.path("benchmark");
        if (!provided(node)) { notProvided(d, "Benchmark"); return; }
        JsonNode b = node.path("benchmark");
        JsonNode s = node.path("summary");
        PdfPTable t = grid(2);
        t.addCell(cell("Benchmark id", text(b, "benchmarkId"), HEADER_CELL, MONO));
        t.addCell(cell("Status", text(b, "status"), HEADER_CELL, BODY));
        t.addCell(cell("Total cases", text(s, "totalCases"), HEADER_CELL, BODY));
        t.addCell(cell("Executed", text(s, "executedCases"), HEADER_CELL, BODY));
        t.addCell(cell("Skipped", text(s, "skippedCases"), HEADER_CELL, BODY));
        t.addCell(cell("Passed", text(s, "passedCases"), HEADER_CELL, BODY));
        t.addCell(cell("Failed", text(s, "failedCases"), HEADER_CELL, BODY));
        t.addCell(cell("Accuracy", text(s, "accuracy"), HEADER_CELL, BODY));
        d.add(t);
        d.add(para(text(s, "interpretation"), SMALL, 8));

        JsonNode cm = s.path("conformanceMetrics");
        if (!cm.isMissingNode()) {
            d.add(heading("Conformance classification metrics", H3, 8));
            PdfPTable m = grid(2);
            m.addCell(cell("Accuracy", text(cm, "accuracy"), HEADER_CELL, BODY));
            m.addCell(cell("Precision", text(cm, "precision"), HEADER_CELL, BODY));
            m.addCell(cell("Recall", text(cm, "recall"), HEADER_CELL, BODY));
            m.addCell(cell("F1", text(cm, "f1"), HEADER_CELL, BODY));
            m.addCell(cell("Positive class", text(cm, "positiveClass"), HEADER_CELL, MONO));
            d.add(m);
            d.add(heading("Confusion matrix (expected rows x actual columns)", H3, 8));
            d.add(confusion(cm.path("confusionMatrix")));
        }
        JsonNode cases = b.path("cases");
        if (cases.isArray() && !cases.isEmpty()) {
            d.add(heading("Benchmark case results", H3, 8));
            PdfPTable t2 = table(4, "Case", "Group", "Status", "Discrepancy");
            for (JsonNode c : cases) {
                t2.addCell(value(text(c, "caseId"), MONO));
                t2.addCell(value(text(c, "group"), MONO));
                t2.addCell(value(text(c, "status"), MONO_BOLD));
                t2.addCell(value(text(c, "discrepancy"), SMALL));
            }
            d.add(t2);
            d.add(para("Negative controls such as CTRL-001 are shown with their real status. Benchmark"
                    + " metrics describe the controlled fixture population only.", SMALL, 6));
        }
    }

    private void limitations(Document d, JsonNode doc) {
        d.add(heading("11. Limitations / Notes", H2, 12));
        JsonNode node = doc.path("limitations");
        if (!provided(node)) { notProvided(d, "Limitations"); return; }
        for (String l : toList(node.path("limitations"))) d.add(para("- " + l, BODY, 3));
        d.add(heading("12. Report Notes", H3, 10));
        d.add(para("- This report is a representation of results produced by earlier APIXA steps."
                + " No analysis was re-run to produce it.", BODY, 3));
        d.add(para("- Conformance, mapping, impact and benchmark values are shown exactly as supplied;"
                + " the report does not modify, re-score or re-order them.", BODY, 3));
        d.add(para("- Runtime entries are observations, not conformance verdicts.", BODY, 3));
        d.add(para("- Credential-bearing headers are masked and no source file content is included.", BODY, 3));
    }

    private void closing(Document d, GeneratedReport report) {
        d.add(heading("End of report", H3, 14));
        d.add(para("Generated by " + report.metadata().generator() + " at "
                + report.metadata().generatedAt(), SMALL, 4));
    }
    /**
     * Repeating page header (report title) and footer (page n of m) on every page.
     *
     * <p>The total page count is only known once the last page is reached, so OpenPDF's classic
     * page-count pattern is used: {@code setPageCount} is fed on every page and the footer reads the
     * total back from the writer when the document closes.
     */
    private void addHeadersAndFooters(Document document, PdfWriter writer, GeneratedReport report) {
        int[] page = { 0 };
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onStartPage(PdfWriter w, com.lowagie.text.Document doc) {
                PdfPTable header = new PdfPTable(1);
                header.setWidthPercentage(100);
                header.setSpacingAfter(8);
                header.addCell(new PdfPCell(new Phrase(safe(report.metadata().title()), SMALL)));
                doc.add(header);
            }

            @Override
            public void onEndPage(PdfWriter w, com.lowagie.text.Document doc) {
                // OpenPDF has no total-page accessor: the writer counter is reset on every page and
                // seeded with the running total, so the final page reads the true total. The running
                // page number is counted here because onEndPage is the only callback this build
                // reliably delivers, and it always runs at least once per page.
                page[0]++;
                w.resetPageCount();
                w.setPageCount(page[0]);
                doc.add(new Phrase("Page " + page[0] + " of " + w.getPageNumber(), SMALL));
            }
        });
    }
    private Paragraph heading(String text, Font font, float spacingAfter) {
        Paragraph p = new Paragraph(safe(text), font);
        p.setSpacingBefore(6);
        p.setSpacingAfter(spacingAfter);
        p.setKeepTogether(true);
        return p;
    }

    private Paragraph para(String text, Font font, float spacingAfter) {
        Paragraph p = new Paragraph(safe(text), font);
        p.setSpacingAfter(spacingAfter);
        return p;
    }

    /** Two-column key/value grid; values wrap, so a long value can never be clipped. */
    private PdfPTable grid(int columns) {
        PdfPTable t = new PdfPTable(columns);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setSpacingAfter(8);
        if (columns == 2) t.setWidths(new float[] { 30f, 70f });
        return t;
    }

    /** Data table with a header row that repeats when the table breaks across pages. */
    private PdfPTable table(int columns, String... headers) {
        PdfPTable t = new PdfPTable(columns);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        t.setSpacingAfter(8);
        t.setWidths(evenWidths(columns));
        for (String h : headers) t.addCell(new PdfPCell(new Phrase(safe(h), HEADER_CELL)));
        t.setHeaderRows(1);
        return t;
    }

    /** One key/value row rendered as a single cell, so long values wrap instead of overflowing. */
    private PdfPCell cell(String label, Object value, Font labelFont, Font valueFont) {
        PdfPCell c = new PdfPCell();
        c.setPadding(4);
        c.setVerticalAlignment(Element.ALIGN_TOP);
        if (label != null) {
            c.addElement(new Phrase(safe(label), labelFont));
            c.addElement(new Phrase(" "));
        }
        c.addElement(new Phrase(safe(value == null ? "" : String.valueOf(value)), valueFont));
        return c;
    }
    private PdfPCell value(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(safe(text), font));
        c.setPadding(3);
        c.setVerticalAlignment(Element.ALIGN_TOP);
        return c;
    }

    private PdfPTable confusion(JsonNode matrix) {
        List<String> cols = new ArrayList<>();
        matrix.fieldNames().forEachRemaining(cols::add);
        PdfPTable t = new PdfPTable(cols.size() + 1);
        t.setWidthPercentage(100);
        t.setSpacingAfter(8);
        t.addCell(new PdfPCell(new Phrase("expected / actual", HEADER_CELL)));
        for (String col : cols) t.addCell(new PdfPCell(new Phrase(safe(col), HEADER_CELL)));
        t.setHeaderRows(1);
        for (String rowName : cols) {
            t.addCell(new PdfPCell(new Phrase(safe(rowName), HEADER_CELL)));
            JsonNode row = matrix.path(rowName);
            for (String col : cols) t.addCell(new PdfPCell(new Phrase(safe(text(row, col)), MONO)));
        }
        return t;
    }

    private PdfPTable counts(JsonNode counts) {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(45);
        t.setWidths(new float[] { 70f, 30f });
        t.setSpacingBefore(10);
        t.setSpacingAfter(8);
        Iterator<Map.Entry<String, JsonNode>> it = counts.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            t.addCell(new PdfPCell(new Phrase(safe(e.getKey()), MONO)));
            t.addCell(new PdfPCell(new Phrase(e.getValue().asText(), MONO)));
        }
        return t;
    }

    private void kv(Document d, String label, JsonNode section, String child) {
        d.add(heading(label, H3, 4));
        if (!provided(section)) {
            d.add(para("Not provided.", BODY, 6));
            return;
        }
        PdfPTable t = grid(2);
        section.path(child).fields().forEachRemaining(f -> t.addCell(cell(f.getKey(),
                f.getValue().isValueNode() ? f.getValue().asText() : f.getValue().toString(),
                HEADER_CELL, BODY)));
        d.add(t);
    }

    private void notProvided(Document d, String what) {
        d.add(para(what + " results were not provided for this report.", BODY, 6));
    }

    /** Even column widths; wide text columns (file, path, implementation) wrap rather than clip. */
    private float[] evenWidths(int columns) {
        float[] widths = new float[columns];
        java.util.Arrays.fill(widths, 100f / columns);
        return widths;
    }

    private boolean provided(JsonNode section) {
        return section != null && section.path("provided").asBoolean(false);
    }

    /** Renders a normalized policy as readable text without re-interpreting it. */
    private String policy(JsonNode p) {
        if (p == null || p.isMissingNode()) return "notProvided";
        if (p.path("unknown").asBoolean()) return "UNKNOWN";
        boolean permitAll = p.path("permitAll").asBoolean();
        String auth = text(p, "authentication");
        if (permitAll || "NONE".equals(auth)) return "PUBLIC (permitAll)";
        List<String> parts = new ArrayList<>();
        parts.add(auth == null ? "AUTHENTICATED" : auth);
        String roles = list(p, "roles");
        String authorities = list(p, "authorities");
        String scopes = list(p, "scopes");
        if (!roles.isBlank()) parts.add("roles " + roles);
        if (!authorities.isBlank()) parts.add("authorities " + authorities);
        if (!scopes.isBlank()) parts.add("scopes " + scopes);
        return String.join(" ", parts);
    }

    /** Joins the non-blank parts with a single space; never renders "null". */
    private String join(String... parts) {
        List<String> out = new ArrayList<>();
        for (String p : parts) if (p != null && !p.isBlank()) out.add(p);
        return String.join(" ", out);
    }

    /** "File.java:44", or an empty string when the item carries no location. */
    private String fileLine(JsonNode node) {
        String file = text(node, "file");
        String line = text(node, "lineStart");
        if (file == null && line == null) return "";
        return file == null ? "line " + line : line == null ? file : file + ":" + line;
    }

    /** The applied Step 9 rule, or an empty string when the conformance result carries none. */
    private String appliedRule(JsonNode applied) {
        String rule = join(text(applied, "operator"), text(applied, "pathPattern"));
        String where = fileLine(applied);
        return rule.isEmpty() ? where : rule + (where.isEmpty() ? "" : " (" + where + ")");
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) return null;
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private String list(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.path(field);
        return v != null && v.isArray() && !v.isEmpty() ? String.join(", ", toList(v)) : "";
    }

    private List<String> toList(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null) array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }

    /**
     * ASCII-safe text for the built-in Helvetica/Courier fonts. Characters outside Latin-1 (em dash,
     * arrows, curly quotes) become ASCII equivalents, so no renderer can emit a missing-glyph box.
     */
    private String safe(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                out.append(' ');
            } else if (c >= 0x20 && c <= 0x7E) {
                out.append(c);                       // printable ASCII passes through unchanged
            } else {
                // Anything outside printable ASCII is transliterated rather than dropped, so the
                // meaning survives and no font can fail on a missing glyph. A hand-written scan is
                // used instead of a regex because regex compilation is not safe on the small stacks
                // of the virtual threads this service runs on.
                out.append(transliterate(c));
            }
        }
        return out.toString();
    }

    private String transliterate(char c) {
        return switch (c) {
            case '\u2014', '\u2013', '\u2212', '\u2012' -> "-";
            case '\u2192', '\u21D2', '\u27F6' -> "->";
            case '\u00D7' -> "x";
            case '\u00B7' -> "-";
            case '\u2018', '\u2019', '\u201A' -> "'";
            case '\u201C', '\u201D', '\u201E' -> "\"";
            case '\u2026' -> "...";
            case '\u00A0' -> " ";
            case '\u2264', '\u2265' -> "<=";
            case '\u2260' -> "!=";
            default -> "?";
        };
    }}