package com.apixa.evidence.service;

import com.apixa.common.error.ApiException;
import com.apixa.evidence.entity.EvidenceEntity;
import com.apixa.evidence.model.EvidenceSetRequest;
import com.apixa.evidence.model.EvidenceSetRequest.ConformanceEvidence;
import com.apixa.evidence.model.EvidenceSetRequest.ContractEvidence;
import com.apixa.evidence.model.EvidenceSetRequest.EndpointEvidence;
import com.apixa.evidence.model.EvidenceSetRequest.MappingCandidate;
import com.apixa.evidence.model.EvidenceSetRequest.MappingEvidence;
import com.apixa.evidence.model.EvidenceSetRequest.SourceEvidence;
import com.apixa.evidence.model.EvidenceSetRequest.SourceSecurityEvidence;
import com.apixa.evidence.model.EvidenceSetResponse;
import com.apixa.evidence.repository.EvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Step 12 evidence service: creation, linkage, persistence and retrieval of the traceability behind a
 * conformance result.
 *
 * <p><b>Evidence is not an analyzer.</b> Nothing here decides MATCH / MISMATCH / PARTIAL / UNVERIFIED —
 * the Step 11 verdict is recorded verbatim, together with the facts that explain it. No OpenAPI
 * document is re-parsed, no Java source is re-scanned and no other APIXA service is called: the caller
 * supplies the already-extracted datasets.
 *
 * <p><b>Chain.</b> For every contract endpoint one set is persisted as
 * {@code CONTRACT → MAPPING → SOURCE → SOURCE_SECURITY → CONFORMANCE}. Each item stores its own
 * {@code EV-...} code and the codes it links to in {@code relatedEvidenceCodes}, so the CONFORMANCE item
 * points back at the exact contract, mapping, source and security-rule records behind the verdict.
 *
 * <p><b>Idempotency.</b> Codes are deterministic ({@code EV-<setId>-<TYPE>-<METHOD>-<PATH>-<n>}), so
 * re-posting the same evidence set updates the same rows instead of duplicating them. No distributed
 * idempotency mechanism is involved.
 *
 * <p><b>Safety.</b> Only factual metadata is stored: identifiers, paths, file/line references, security
 * summaries and the Step 11 reason. No source file bodies, no OpenAPI documents, no credentials.
 *
 * <p>The pre-existing low-level CRUD ({@code store}, {@code byCode}, {@code byRun}, {@code all}) is
 * preserved unchanged.
 */
@Service
public class EvidenceService {

    private final EvidenceRepository repository;

    public EvidenceService(EvidenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EvidenceEntity store(EvidenceEntity evidence) {
        return repository.save(evidence);
    }

    public EvidenceEntity byCode(String code) {
        return repository.findByEvidenceCode(code)
                .orElseThrow(() -> ApiException.notFound("Evidence " + code + " not found"));
    }

    public List<EvidenceEntity> byRun(Long analysisRunId) {
        return repository.findByAnalysisRunIdOrderByIdAsc(analysisRunId);
    }

    public List<EvidenceEntity> all() { return repository.findAll(); }

    /** Step 12: the whole chain for one evidence set, in deterministic creation order. */
    public List<EvidenceEntity> bySet(String setCode) {
        if (setCode == null || setCode.isBlank()) {
            throw ApiException.badRequest("evidenceSetId is required");
        }
        return repository.findByEvidenceSetCodeOrderByIdAsc(setCode);
    }

    /**
     * Creates (or refreshes) the linked evidence set for the supplied endpoint chains.
     */
    @Transactional
    public EvidenceSetResponse createSet(EvidenceSetRequest request) {
        if (request == null) throw ApiException.badRequest("Evidence request body is required");
        if (request.endpoints() == null || request.endpoints().isEmpty()) {
            throw ApiException.badRequest("endpoints is required and must not be empty");
        }
        String setCode = request.evidenceSetId();
        if (setCode == null || setCode.isBlank()) {
            // Deterministic fallback from identifiers APIXA already has (no random correlation id).
            setCode = "ES-" + (request.analysisRunId() == null ? "0" : request.analysisRunId())
                    + "-" + (request.contractId() == null ? "unknown" : request.contractId());
        }

        List<EvidenceEntity> saved = new ArrayList<>();
        int seq = 0;
        for (EndpointEvidence ep : request.endpoints()) {
            if (ep == null) continue;
            seq++;
            saved.addAll(createChain(setCode, request, ep, seq));
        }
        if (saved.isEmpty()) throw ApiException.badRequest("No evidence could be created from the request");
        return new EvidenceSetResponse(setCode, request.analysisRunId(), request.contractId(),
                saved.size(), List.copyOf(saved));
    }
    private List<EvidenceEntity> createChain(String setCode, EvidenceSetRequest request,
                                             EndpointEvidence ep, int seq) {
        List<EvidenceEntity> out = new ArrayList<>();
        ContractEvidence ce = ep.contract();
        String method = ce != null && ce.method() != null ? ce.method() : "ANY";
        String path = ce != null ? ce.path() : null;
        String suffix = seq + "-" + safe(method) + "-" + safe(path);

        String contractCode = null;
        if (ce != null) {
            contractCode = code(setCode, "CONTRACT", suffix);
            String cc = contractCode;
            out.add(upsert(setCode, request, cc, "CONTRACT", method, path, e -> {
                e.setFile(ce.sourceFile());
                e.setJsonPath(ce.jsonPath());          // $.paths['/admin/users'].get.security
                e.setRuleType("OPERATION");
                e.setDescription("Contract declares " + method + " " + path
                        + (ce.operationId() == null ? "" : " (operationId " + ce.operationId() + ")")
                        + " expecting " + ce.securitySummary());
                e.setContent(json(Map.of(
                        "operationId", String.valueOf(ce.operationId()),
                        "expectedSecurity", String.valueOf(ce.securitySummary()),
                        "documentRef", String.valueOf(ce.documentRef()))));
            }));
        }

        String mappingCode = null;
        MappingEvidence me = ep.mapping();
        if (me != null) {
            mappingCode = code(setCode, "MAPPING", suffix);
            String mc = mappingCode;
            String rel = contractCode;
            out.add(upsert(setCode, request, mc, "MAPPING", method, path, e -> {
                e.setPathPattern(me.sourcePath());
                e.setRuleType(me.status());
                e.setDescription("Endpoint mapping " + me.status()
                        + (me.confidence() == null ? "" : " (" + me.confidence() + " confidence)")
                        + " to " + me.sourceMethod());
                // Candidates are preserved verbatim, including MULTIPLE_CANDIDATES; none is selected.
                e.setContent(json(Map.of(
                        "status", String.valueOf(me.status()),
                        "confidence", String.valueOf(me.confidence()),
                        "reason", String.valueOf(me.reason()),
                        "sourceMethod", String.valueOf(me.sourceMethod()),
                        "sourcePath", String.valueOf(me.sourcePath()),
                        "candidates", candidates(me.candidates()))));
                e.setRelatedEvidenceCodes(rel);
            }));
        }

        List<String> sourceCodes = new ArrayList<>();
        if (ep.sourceEndpoints() != null) {
            int i = 0;
            for (SourceEvidence se : ep.sourceEndpoints()) {
                if (se == null) continue;
                i++;
                String sc = code(setCode, "SOURCE", suffix + "-" + i);
                sourceCodes.add(sc);
                String rel = mappingCode != null ? mappingCode : String.join(",", prevOf(sourceCodes));
                out.add(upsert(setCode, request, sc, "SOURCE",
                        se.httpMethod() == null ? method : se.httpMethod(), se.path(), e -> {
                            e.setFile(se.file());
                            e.setLineStart(se.lineStart());
                            e.setLineEnd(se.lineEnd());
                            e.setRuleType("ENDPOINT");
                            e.setDescription("Source endpoint " + se.className() + "." + se.methodName());
                            e.setContent(json(Map.of(
                                    "className", String.valueOf(se.className()),
                                    "methodName", String.valueOf(se.methodName()))));
                            e.setRelatedEvidenceCodes(rel);
                        }));
            }
        }
        String securityCode = null;
        SourceSecurityEvidence ss = ep.sourceSecurity();
        if (ss != null) {
            securityCode = code(setCode, "SOURCE_SECURITY", suffix);
            String rel = sourceCodes.isEmpty() ? mappingCode : String.join(",", sourceCodes);
            out.add(upsert(setCode, request, securityCode, "SOURCE_SECURITY", method, path, e -> {
                e.setFile(ss.file());
                e.setLineStart(ss.lineStart());
                e.setLineEnd(ss.lineEnd());
                e.setPathPattern(ss.pathPattern());
                e.setRuleType(ss.operator());
                // Only the rule's own metadata — never the surrounding source code.
                e.setDescription("Source security rule " + ss.operator() + " " + ss.pathPattern()
                        + " (" + scopeLabel(ss) + ")");
                e.setContent(json(Map.of(
                        "scope", String.valueOf(ss.scope()),
                        "operator", String.valueOf(ss.operator()),
                        "pathPattern", String.valueOf(ss.pathPattern()),
                        "expression", String.valueOf(ss.expression()),
                        "roles", ss.roles() == null ? List.of() : ss.roles(),
                        "authorities", ss.authorities() == null ? List.of() : ss.authorities(),
                        "unresolved", String.valueOf(ss.unresolved()),
                        "complex", String.valueOf(ss.complex()))));
                e.setRelatedEvidenceCodes(rel);
            }));
        }

        ConformanceEvidence cf = ep.conformance();
        if (cf != null) {
            String confCode = code(setCode, "CONFORMANCE", suffix);
            List<String> related = new ArrayList<>();
            if (contractCode != null) related.add(contractCode);
            if (mappingCode != null) related.add(mappingCode);
            related.addAll(sourceCodes);
            if (securityCode != null) related.add(securityCode);
            out.add(upsert(setCode, request, confCode, "CONFORMANCE", method, path, e -> {
                e.setRuleType(cf.status());
                e.setDescription("Conformance " + cf.status() + " (mapping " + cf.mappingStatus() + ")");
                // The Step 11 verdict and reason are recorded verbatim — never recomputed here.
                e.setContent(json(Map.of(
                        "conformanceStatus", String.valueOf(cf.status()),
                        "mappingStatus", String.valueOf(cf.mappingStatus()),
                        "reason", String.valueOf(cf.reason()),
                        "expectedSecurity", String.valueOf(cf.expectedSummary()),
                        "implementedSecurity", String.valueOf(cf.implementedSummary()))));
                e.setRelatedEvidenceCodes(related.isEmpty() ? null : String.join(",", related));
            }));
        }
        return out;
    }

    private List<String> prevOf(List<String> codes) {
        return codes.subList(0, codes.size() - 1);
    }

    private String scopeLabel(SourceSecurityEvidence ss) {
        if (Boolean.TRUE.equals(ss.unresolved())) return "unresolved argument";
        if (Boolean.TRUE.equals(ss.complex())) return "complex expression";
        return ss.scope() == null ? "SECURITY_CONFIGURATION" : ss.scope();
    }

    private List<Map<String, Object>> candidates(List<MappingCandidate> candidates) {
        if (candidates == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (MappingCandidate c : candidates) {
            if (c == null) continue;
            out.add(Map.of(
                    "className", String.valueOf(c.className()),
                    "methodName", String.valueOf(c.methodName()),
                    "httpMethod", String.valueOf(c.httpMethod()),
                    "path", String.valueOf(c.path()),
                    "file", String.valueOf(c.file()),
                    "lineStart", String.valueOf(c.lineStart())));
        }
        return out;
    }

    /**
     * Deterministic code: re-posting the same set/endpoint/type updates the same row instead of
     * creating a duplicate.
     */
    private String code(String setCode, String type, String suffix) {
        return "EV-" + setCode + "-" + type + "-" + suffix;
    }

    private EvidenceEntity upsert(String setCode, EvidenceSetRequest request, String evidenceCode,
                                  String type, String method, String path, Customizer customizer) {
        EvidenceEntity e = repository.findByEvidenceCode(evidenceCode).orElseGet(EvidenceEntity::new);
        e.setEvidenceCode(evidenceCode);
        e.setSourceType(type);
        e.setEvidenceSetCode(setCode);
        e.setHttpMethod(method);
        e.setEndpointPath(path);
        e.setAnalysisRunId(request.analysisRunId());
        customizer.apply(e);
        return repository.save(e);
    }

    @FunctionalInterface
    private interface Customizer { void apply(EvidenceEntity e); }

    /**
     * Minimal deterministic JSON writer for the evidence payload. Written locally so this module keeps
     * its existing dependency set (no new Jackson dependency); key order follows insertion order.
     */
    private String json(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(e.getKey())).append(':').append(value(e.getValue()));
        }
        return sb.append('}').toString();
    }

    private String value(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return quote(s);
        if (v instanceof Boolean || v instanceof Number) return String.valueOf(v);
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            m.forEach((k, val) -> copy.put(String.valueOf(k), val));
            return json(copy);
        }
        if (v instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                sb.append(value(o));
            }
            return sb.append(']').toString();
        }
        return quote(String.valueOf(v));
    }

    private String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private String safe(String s) {
        if (s == null) return "none";
        String v = s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-").replaceAll("(^-|-$)", "");
        return v.isEmpty() ? "none" : v;
    }
}
