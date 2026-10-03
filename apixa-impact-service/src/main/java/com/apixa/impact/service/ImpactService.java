package com.apixa.impact.service;

import com.apixa.common.error.ApiException;
import com.apixa.common.model.SecurityPolicy;
import com.apixa.impact.model.ImpactRequest;
import com.apixa.impact.model.ImpactRequest.EndpointSnapshot;
import com.apixa.impact.model.ImpactRequest.RuntimeObservation;
import com.apixa.impact.model.ImpactRequest.SecurityRuleSnapshot;
import com.apixa.impact.model.ImpactRequest.VersionSnapshot;
import com.apixa.impact.model.ImpactResult;
import com.apixa.impact.model.ImpactResult.Category;
import com.apixa.impact.model.ImpactResult.ImpactRecord;
import com.apixa.impact.model.ImpactResult.ImpactRecord.Side;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Step 14 change-impact engine: compares two already-computed APIXA snapshots and reports what differs.
 *
 * <p><b>Comparison only.</b> Nothing is re-derived here — no OpenAPI re-parse, no source re-analysis, no
 * mapping or conformance re-run, no active runtime testing. Every value reported comes from the input.
 *
 * <p><b>Endpoint identity</b> = HTTP method + normalized path <em>structure</em>. Path-variable names are
 * equivalent, reusing Step 10's segment semantics ({@code /users/{id}} and {@code /users/{userId}} are one
 * logical endpoint). Original V1 and V2 paths are always preserved and never rewritten. Because the
 * method is part of the identity, {@code GET /users} → {@code POST /users} is reported as one removal
 * plus one addition, never as "unchanged".
 *
 * <p><b>Security comparison</b> uses normalized, order-insensitive set comparison of roles, authorities
 * and scopes; no scope↔authority equivalence is invented and textual ordering alone is not a change.
 * A policy that was not supplied is {@code null} (unknown) and is never treated as public.
 *
 * <p>No severity or ranking is produced: records state facts only.
 */
@Service
public class ImpactService {

    public ImpactResult analyze(ImpactRequest request) {
        if (request == null) throw ApiException.badRequest("Impact request body is required");
        if (request.v1() == null) throw ApiException.badRequest("v1 is required");
        if (request.v2() == null) throw ApiException.badRequest("v2 is required");
        if (request.v1().endpoints() == null && request.v1().securityRules() == null) {
            throw ApiException.badRequest("v1 must supply endpoints or securityRules");
        }
        if (request.v2().endpoints() == null && request.v2().securityRules() == null) {
            throw ApiException.badRequest("v2 must supply endpoints or securityRules");
        }

        List<ImpactRecord> impacts = new ArrayList<>();
        impacts.addAll(compareEndpoints(request.v1(), request.v2()));
        impacts.addAll(compareSecurityRules(request.v1(), request.v2()));
        impacts.addAll(compareRuntime(request.v1(), request.v2()));

        // Deterministic order: path, then method, then category, then the stable V1/V2 references.
        impacts.sort(Comparator
                .comparing((ImpactRecord i) -> i.path() == null ? "" : i.path())
                .thenComparing(i -> i.method() == null ? "" : i.method())
                .thenComparing(i -> i.category())
                .thenComparing(i -> String.valueOf(i.v1EvidenceCodes()))
                .thenComparing(i -> String.valueOf(i.v2EvidenceCodes()))
                .thenComparing(i -> String.valueOf(i.details())));

        String projectId = Optional.ofNullable(request.v1().projectId()).orElseGet(() -> request.v2().projectId());
        return new ImpactResult(projectId, request.v1().versionId(), request.v2().versionId(),
                request.v1().analysisRunId(), request.v2().analysisRunId(),
                request.v1().contractId(), request.v2().contractId(),
                ImpactResult.COMPLETED, impacts.size(), List.copyOf(impacts));
    }
    private List<ImpactRecord> compareEndpoints(VersionSnapshot v1, VersionSnapshot v2) {
        Map<String, EndpointSnapshot> oldOnes = indexEndpoints(v1.endpoints());
        Map<String, EndpointSnapshot> newOnes = indexEndpoints(v2.endpoints());
        Map<String, RuntimeObservation> oldRt = indexRuntime(v1.runtimeObservations());
        Map<String, RuntimeObservation> newRt = indexRuntime(v2.runtimeObservations());

        List<ImpactRecord> out = new ArrayList<>();
        for (Map.Entry<String, EndpointSnapshot> e : newOnes.entrySet()) {
            EndpointSnapshot nw = e.getValue();
            EndpointSnapshot old = oldOnes.get(e.getKey());
            if (old == null) {
                out.add(new ImpactRecord(nw.method(), nw.path(), Category.ADDED_ENDPOINT, null, nw.path(),
                        null, side(nw, null), null, codes(nw.evidenceCodes()),
                        "Endpoint present in V2 but not in V1"));
                continue;
            }
            // Security: expected and implemented policies are tracked independently.
            if (changed(expectedSecurity(old), expectedSecurity(nw))) {
                out.add(record(nw, old, securityCategory(expectedSecurity(old), expectedSecurity(nw)),
                        "Expected security changed from " + summary(expectedSecurity(old))
                                + " to " + summary(expectedSecurity(nw))));
            }
            if (changed(implementedSecurity(old), implementedSecurity(nw))) {
                out.add(record(nw, old, securityCategory(implementedSecurity(old), implementedSecurity(nw)),
                        "Implemented security changed from " + summary(implementedSecurity(old))
                                + " to " + summary(implementedSecurity(nw))));
            }
            // Conformance: reported as a difference, never judged.
            if (changed(conformance(old), conformance(nw))) {
                out.add(record(nw, old, Category.CONFORMANCE_CHANGED,
                        "Conformance changed from " + conformance(old) + " to " + conformance(nw)));
            }
            StringBuilder changed = new StringBuilder();
            if (changed(operationId(old), operationId(nw))) changed.append("operationId ");
            if (changed(mappingStatus(old), mappingStatus(nw))) changed.append("mappingStatus ");
            if (changed.length() > 0) {
                out.add(record(nw, old, Category.MODIFIED_ENDPOINT,
                        "Tracked attributes changed: " + changed.toString().trim()));
            }
            // Runtime: observational difference only, never a verdict.
            RuntimeObservation o = oldRt.get(e.getKey());
            RuntimeObservation n = newRt.get(e.getKey());
            if (o != null && n != null && !Objects.equals(o.observedStatusCode(), n.observedStatusCode())) {
                out.add(new ImpactRecord(nw.method(), nw.path(), Category.RUNTIME_OBSERVATION_CHANGED,
                        old.path(), nw.path(), side(old, o), side(nw, n),
                        codes(old.evidenceCodes()), codes(nw.evidenceCodes()),
                        "Runtime observation changed from " + o.observedStatusCode()
                                + " to " + n.observedStatusCode() + " (observation only)"));
            }
        }
        for (Map.Entry<String, EndpointSnapshot> e : oldOnes.entrySet()) {
            if (newOnes.containsKey(e.getKey())) continue;
            EndpointSnapshot old = e.getValue();
            out.add(new ImpactRecord(old.method(), old.path(), Category.REMOVED_ENDPOINT,
                    old.path(), null, side(old, null), null, codes(old.evidenceCodes()), null,
                    "Endpoint present in V1 but not in V2"));
        }
        return out;
    }
    private List<ImpactRecord> compareSecurityRules(VersionSnapshot v1, VersionSnapshot v2) {
        Map<String, SecurityRuleSnapshot> oldOnes = indexRules(v1.securityRules());
        Map<String, SecurityRuleSnapshot> newOnes = indexRules(v2.securityRules());
        List<ImpactRecord> out = new ArrayList<>();
        for (Map.Entry<String, SecurityRuleSnapshot> e : newOnes.entrySet()) {
            SecurityRuleSnapshot nw = e.getValue();
            SecurityRuleSnapshot old = oldOnes.get(e.getKey());
            if (old == null) {
                out.add(ruleRecord(nw, null, Category.ADDED_ENDPOINT,
                        "Source security rule added: " + ruleLabel(nw)));
            } else if (changed(old.policy(), nw.policy()) || changed(old.operator(), nw.operator())) {
                out.add(ruleRecord(nw, old, securityCategory(old.policy(), nw.policy()),
                        "Source security rule changed from " + ruleLabel(old)
                                + " to " + ruleLabel(nw)));
            }
        }
        for (Map.Entry<String, SecurityRuleSnapshot> e : oldOnes.entrySet()) {
            if (newOnes.containsKey(e.getKey())) continue;
            out.add(ruleRecord(e.getValue(), null, Category.REMOVED_ENDPOINT,
                    "Source security rule removed: " + ruleLabel(e.getValue())));
        }
        return out;
    }

    private List<ImpactRecord> compareRuntime(VersionSnapshot v1, VersionSnapshot v2) {
        Map<String, RuntimeObservation> oldOnes = indexRuntime(v1.runtimeObservations());
        Map<String, RuntimeObservation> newOnes = indexRuntime(v2.runtimeObservations());
        List<ImpactRecord> out = new ArrayList<>();
        for (Map.Entry<String, RuntimeObservation> e : newOnes.entrySet()) {
            if (oldOnes.containsKey(e.getKey())) continue;   // already compared alongside its endpoint
            out.add(new ImpactRecord(e.getValue().method(), e.getValue().path(),
                    Category.RUNTIME_OBSERVATION_CHANGED, null, e.getValue().path(),
                    null, obs(e.getValue()), null, null,
                    "Runtime observation present in V2 but not in V1 (observation only)"));
        }
        for (Map.Entry<String, RuntimeObservation> e : oldOnes.entrySet()) {
            if (newOnes.containsKey(e.getKey())) continue;
            out.add(new ImpactRecord(e.getValue().method(), e.getValue().path(),
                    Category.RUNTIME_OBSERVATION_CHANGED, e.getValue().path(), null,
                    obs(e.getValue()), null, null, null,
                    "Runtime observation present in V1 but not in V2 (observation only)"));
        }
        return out;
    }

    private ImpactRecord record(EndpointSnapshot nw, EndpointSnapshot old, Category category, String details) {
        return new ImpactRecord(nw.method(), nw.path(), category, old.path(), nw.path(),
                side(old, null), side(nw, null), codes(old.evidenceCodes()), codes(nw.evidenceCodes()), details);
    }

    private ImpactRecord ruleRecord(SecurityRuleSnapshot nw, SecurityRuleSnapshot old, Category category, String details) {
        return new ImpactRecord(nw.operator() == null ? "RULE" : nw.operator(), nw.pathPattern(), category,
                old == null ? null : old.pathPattern(), nw.pathPattern(),
                old == null ? null : new Side(null, summary(old.policy()), null, null, null, null),
                new Side(null, summary(nw.policy()), null, null, null, null),
                old == null ? null : codes(old.evidenceCodes()), codes(nw.evidenceCodes()), details);
    }
    private Map<String, EndpointSnapshot> indexEndpoints(List<EndpointSnapshot> endpoints) {
        Map<String, EndpointSnapshot> map = new LinkedHashMap<>();
        if (endpoints == null) return map;
        for (EndpointSnapshot e : endpoints) {
            if (e == null || e.path() == null) continue;
            map.putIfAbsent(key(e.method(), e.path()), e);
        }
        return map;
    }

    private Map<String, RuntimeObservation> indexRuntime(List<RuntimeObservation> observations) {
        Map<String, RuntimeObservation> map = new LinkedHashMap<>();
        if (observations == null) return map;
        for (RuntimeObservation o : observations) {
            if (o == null || o.path() == null) continue;
            map.putIfAbsent(key(o.method(), o.path()), o);
        }
        return map;
    }

    private Map<String, SecurityRuleSnapshot> indexRules(List<SecurityRuleSnapshot> rules) {
        Map<String, SecurityRuleSnapshot> map = new LinkedHashMap<>();
        if (rules == null) return map;
        for (SecurityRuleSnapshot r : rules) {
            if (r == null) continue;
            map.putIfAbsent(key(r.operator(), r.pathPattern()), r);
        }
        return map;
    }

    /**
     * Endpoint identity: HTTP method + normalized path <em>structure</em>. Path-variable names are
     * equivalent (Step 10 segment semantics), so a rename is not an add+remove pair. Trailing slashes are
     * insignificant. Nothing here rewrites the reported path.
     */
    private String key(String method, String path) {
        String m = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        return m + " " + structure(path);
    }

    private String structure(String path) {
        if (path == null) return "";
        String p = path.trim();
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.isEmpty()) return "";
        if (p.charAt(0) != '/') p = "/" + p;
        return "/" + String.join("/", Arrays.stream(p.substring(1).split("/", -1))
                .map(this::maskVariable).toList());
    }

    private String maskVariable(String segment) {
        return segment.length() > 2 && segment.charAt(0) == '{' && segment.endsWith("}") ? "{}" : segment;
    }

    /**
     * Order-insensitive, normalized policy comparison. Lists are sorted so a textual reordering is not a
     * change, and no scope↔authority equivalence is invented. A {@code null} policy means "not supplied"
     * and stays distinct from an explicitly public one.
     */
    private boolean changed(String a, String b) {
        return !Objects.equals(a, b);
    }

    private boolean changed(SecurityPolicy a, SecurityPolicy b) {
        if (a == null || b == null) return a != b;   // absent is never silently equal to public
        return a.unknown() != b.unknown()
                || a.permitAll() != b.permitAll()
                || !Objects.equals(a.authentication(), b.authentication())
                || !sorted(a.effectiveRoles()).equals(sorted(b.effectiveRoles()))
                || !sorted(a.effectiveAuthorities()).equals(sorted(b.effectiveAuthorities()))
                || !sorted(a.effectiveScopes()).equals(sorted(b.effectiveScopes()));
    }

    /**
     * Category for a security difference. When either side is unknown or not supplied the difference is
     * about data availability, not about a proven policy change, so it is reported factually as
     * {@link Category#SECURITY_INFORMATION_CHANGED}. Two known policies that genuinely differ stay
     * {@link Category#SECURITY_CHANGED}. The structural comparison itself ({@link #changed}) is unchanged.
     */
    private Category securityCategory(SecurityPolicy a, SecurityPolicy b) {
        return isUnknownOrAbsent(a) || isUnknownOrAbsent(b)
                ? Category.SECURITY_INFORMATION_CHANGED
                : Category.SECURITY_CHANGED;
    }

    /** A policy was not supplied at all, or was supplied as explicitly unknown. Never equal to PUBLIC. */
    private boolean isUnknownOrAbsent(SecurityPolicy p) {
        return p == null || p.unknown();
    }

    private List<String> sorted(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).sorted().toList();
    }

    /** Human-readable but purely factual summary; {@code NOT_PROVIDED} keeps absent ≠ public. */
    private String summary(SecurityPolicy p) {
        if (p == null) return "NOT_PROVIDED";
        if (p.unknown()) return "UNKNOWN";
        if (p.permitAll() || "NONE".equals(p.authentication())) return "PUBLIC";
        List<String> parts = new ArrayList<>();
        parts.add(p.authentication() == null ? "AUTHENTICATED" : p.authentication());
        if (!sorted(p.effectiveRoles()).isEmpty()) parts.add("roles " + sorted(p.effectiveRoles()));
        if (!sorted(p.effectiveAuthorities()).isEmpty()) parts.add("authorities " + sorted(p.effectiveAuthorities()));
        if (!sorted(p.effectiveScopes()).isEmpty()) parts.add("scopes " + sorted(p.effectiveScopes()));
        return String.join(" ", parts);
    }

    private String ruleLabel(SecurityRuleSnapshot r) {
        if (r == null) return "NOT_PROVIDED";
        return (r.operator() == null ? "RULE" : r.operator()) + " " + r.pathPattern() + " " + summary(r.policy());
    }
    private Side side(EndpointSnapshot e, RuntimeObservation o) {
        if (e == null) return null;
        return new Side(e.operationId(), summary(e.expectedSecurity()), summary(e.implementedSecurity()),
                e.mappingStatus(), e.conformanceStatus(),
                o == null ? null : o.observedStatusCode());
    }

    private Side obs(RuntimeObservation o) {
        return new Side(null, null, null, null, null, o.observedStatusCode());
    }

    private List<String> codes(List<String> codes) {
        return codes == null || codes.isEmpty() ? null : List.copyOf(codes);
    }

    private SecurityPolicy expectedSecurity(EndpointSnapshot e) { return e == null ? null : e.expectedSecurity(); }
    private SecurityPolicy implementedSecurity(EndpointSnapshot e) { return e == null ? null : e.implementedSecurity(); }
    private String operationId(EndpointSnapshot e) { return e == null ? null : e.operationId(); }
    private String mappingStatus(EndpointSnapshot e) { return e == null ? null : e.mappingStatus(); }
    private String conformance(EndpointSnapshot e) { return e == null ? null : e.conformanceStatus(); }
}