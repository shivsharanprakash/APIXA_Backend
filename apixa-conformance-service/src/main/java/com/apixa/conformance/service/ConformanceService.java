package com.apixa.conformance.service;

import com.apixa.common.error.ApiException;
import com.apixa.common.model.SecurityPolicy;
import com.apixa.conformance.engine.SecurityConformanceEngine;
import com.apixa.conformance.engine.SecurityConformanceEngine.Finding;
import com.apixa.conformance.engine.SecurityConformanceEngine.Result;
import com.apixa.conformance.model.ConformanceRequest;
import com.apixa.conformance.model.ConformanceRequest.ContractEndpointInput;
import com.apixa.conformance.model.ConformanceRequest.MappingInput;
import com.apixa.conformance.model.ConformanceRequest.SecurityRuleInput;
import com.apixa.conformance.model.ConformanceResultDto;
import com.apixa.conformance.model.ConformanceResultDto.AppliedRule;
import com.apixa.conformance.model.ConformanceResultDto.SourceEndpointView;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Step 11 conformance orchestration: mapping verdict + expected/implemented security → conformance
 * verdict.
 *
 * <p>This service performs <b>no discovery</b>: no OpenAPI document is re-parsed, no Java source is
 * re-scanned and no local path is opened. It consumes only the structured output of Step 7 (contract
 * endpoints), Step 10 (mapping results) and Step 9 (source security rules).
 *
 * <p><b>Mapping gate.</b> Only a Step 10 {@code MATCHED} endpoint can reach a security verdict.
 * {@code UNMATCHED}, {@code MULTIPLE_CANDIDATES} and {@code UNCERTAIN} mappings become
 * {@code UNVERIFIED} — an absent or ambiguous implementation is <em>never</em> a MISMATCH, because a
 * MISMATCH would assert a security contradiction that was never established.
 *
 * <p><b>Security rule precedence</b> (highest rank wins; only the best rank participates, so a broad
 * fallback never competes with a concrete rule):
 * <ol>
 *   <li>5000 — {@code METHOD} annotation rule whose class <em>and</em> method match the mapped endpoint</li>
 *   <li>4000 — {@code CLASS} annotation rule whose class matches the mapped endpoint</li>
 *   <li>3000 + segment count — path rule with no wildcard matching the endpoint path
 *       ({@code {variable}} segments match by structure only)</li>
 *   <li>2000 + literal segment count — wildcard path rule whose literal prefix matches
 *       ({@code /admin/**} therefore beats {@code anyRequest()})</li>
 *   <li>1000 — catch-all ({@code **} / {@code anyRequest()}) fallback</li>
 * </ol>
 * Two or more rules tying at the best rank with <em>different</em> policies cannot be resolved safely
 * and yield {@code UNVERIFIED} rather than an arbitrary choice.
 *
 * <p>The security comparison itself is delegated unchanged to the pre-existing
 * {@link SecurityConformanceEngine}; this class only selects the inputs and gates on the mapping.
 */
@Service
public class ConformanceService {

    private static final int RANK_METHOD = 5000;
    private static final int RANK_CLASS = 4000;
    private static final int RANK_PATH_EXACT = 3000;
    private static final int RANK_PATH_WILDCARD = 2000;
    private static final int RANK_CATCH_ALL = 1000;

    private final SecurityConformanceEngine engine;

    public ConformanceService(SecurityConformanceEngine engine) { this.engine = engine; }

    public List<ConformanceResultDto> analyze(ConformanceRequest request) {
        if (request == null) throw ApiException.badRequest("Conformance request body is required");
        if (request.contractEndpoints() == null || request.contractEndpoints().isEmpty()) {
            throw ApiException.badRequest("contractEndpoints is required and must not be empty");
        }
        List<SecurityRuleInput> rules = request.securityRules() == null ? List.of() : request.securityRules();

        // Deterministic output: contract path, then HTTP method. Request order never leaks out.
        List<ContractEndpointInput> ordered = new ArrayList<>(request.contractEndpoints());
        ordered.sort(Comparator.comparing(ContractEndpointInput::path, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ContractEndpointInput::method, Comparator.nullsFirst(String::compareTo)));

        List<ConformanceResultDto> out = new ArrayList<>();
        for (ContractEndpointInput ce : ordered) {
            out.add(one(ce, findMapping(request.mappings(), ce), rules));
        }
        return List.copyOf(out);
    }
    private ConformanceResultDto one(ContractEndpointInput ce, MappingInput mapping,
                                    List<SecurityRuleInput> rules) {
        SecurityPolicy expected = ce.expectedSecurity();
        String mappingStatus = mapping == null ? null : mapping.status();

        // Every candidate from Step 10 is preserved, deterministically ordered.
        List<SourceEndpointView> candidates = candidatesOf(mapping);

        if (mapping == null || !"MATCHED".equals(mappingStatus)) {
            return new ConformanceResultDto(ce.method(), ce.path(), ce.operationId(), mappingStatus,
                    Result.UNVERIFIED.name(), expected, null, null, null, candidates,
                    reasonForUnverifiedMapping(mapping, mappingStatus));
        }

        SourceEndpointView endpoint = new SourceEndpointView(mapping.implementationClass(),
                mapping.implementationMethod(), ce.method(), mapping.implementationPath(), null, null);
        Selection selection = selectRule(rules, mapping.implementationClass(),
                mapping.implementationMethod(), mapping.implementationPath());

        if (selection == null) {
            return new ConformanceResultDto(ce.method(), ce.path(), ce.operationId(), mappingStatus,
                    Result.UNVERIFIED.name(), expected, null, endpoint, null, candidates,
                    "No source security rule applies to the mapped implementation "
                            + mapping.implementationClass() + "." + mapping.implementationMethod() + ".");
        }
        if (selection.conflict()) {
            return new ConformanceResultDto(ce.method(), ce.path(), ce.operationId(), mappingStatus,
                    Result.UNVERIFIED.name(), expected, null, endpoint, selection.view(), candidates,
                    selection.conflictReason());
        }

        SecurityPolicy implemented = selection.policy();
        Finding finding = engine.compare(expected, implemented);   // pre-existing comparison, unchanged
        return new ConformanceResultDto(ce.method(), ce.path(), ce.operationId(), mappingStatus,
                finding.result().name(), expected, implemented, endpoint, selection.view(), candidates,
                finding.reason());
    }

    private String reasonForUnverifiedMapping(MappingInput mapping, String status) {
        if (mapping == null) {
            return "Contract endpoint has no mapping result; conformance cannot be determined.";
        }
        return switch (status == null ? "" : status) {
            case "UNMATCHED" -> "Contract endpoint has no uniquely mapped implementation.";
            case "MULTIPLE_CANDIDATES" ->
                    "Multiple source endpoint candidates prevent a unique conformance decision.";
            case "UNCERTAIN" ->
                    "Endpoint mapping is uncertain, so the applicable implementation security cannot be determined.";
            default ->
                    "Mapping status '" + status + "' does not identify a single implementation endpoint.";
        };
    }

    private MappingInput findMapping(List<MappingInput> mappings, ContractEndpointInput ce) {
        if (mappings == null) return null;
        // Deterministic: the lowest-sorting matching entry wins, so duplicated input cannot change output.
        return mappings.stream()
                .filter(m -> m != null && eq(m.path(), ce.path()) && eq(m.method(), ce.method()))
                .min(Comparator.comparing(MappingInput::status, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(MappingInput::path, Comparator.nullsFirst(String::compareTo)))
                .orElse(null);
    }

    private List<SourceEndpointView> candidatesOf(MappingInput mapping) {
        if (mapping == null || mapping.candidates() == null) return List.of();
        List<SourceEndpointView> views = new ArrayList<>();
        for (MappingInput.CandidateInput c : mapping.candidates()) {
            if (c == null) continue;
            views.add(new SourceEndpointView(c.className(), c.methodName(), c.httpMethod(), c.path(),
                    c.file(), c.lineStart()));
        }
        views.sort(Comparator.comparing(SourceEndpointView::path, Comparator.nullsFirst(String::compareTo))
                .thenComparing(SourceEndpointView::httpMethod, Comparator.nullsFirst(String::compareTo))
                .thenComparing(SourceEndpointView::file, Comparator.nullsFirst(String::compareTo))
                .thenComparing(SourceEndpointView::lineStart, Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(SourceEndpointView::className, Comparator.nullsFirst(String::compareTo))
                .thenComparing(SourceEndpointView::methodName, Comparator.nullsFirst(String::compareTo)));
        return List.copyOf(views);
    }
    /** Selected rule plus the policy it implies, or a same-rank conflict. */
    private record Selection(SecurityPolicy policy, AppliedRule view, boolean conflict, String conflictReason) {}

    /**
     * Specificity-based selection of the one source security rule that applies to the mapped endpoint.
     * Returns {@code null} when no rule applies at all.
     */
    private Selection selectRule(List<SecurityRuleInput> rules, String className, String methodName,
                                 String path) {
        int best = Integer.MIN_VALUE;
        List<SecurityRuleInput> winners = new ArrayList<>();
        for (SecurityRuleInput rule : rules) {
            if (rule == null) continue;
            int rank = rank(rule, className, methodName, path);
            if (rank == Integer.MIN_VALUE) continue;
            if (rank > best) { best = rank; winners.clear(); }
            if (rank == best) winners.add(rule);
        }
        if (winners.isEmpty()) return null;
        // Deterministic representative among tied rules.
        winners.sort(Comparator.comparing((SecurityRuleInput r) -> String.valueOf(r.file()),
                        Comparator.nullsFirst(String::compareTo))
                .thenComparingInt(r -> r.lineStart() == null ? Integer.MAX_VALUE : r.lineStart())
                .thenComparing(r -> String.valueOf(r.operator()), Comparator.nullsFirst(String::compareTo))
                .thenComparing(r -> String.valueOf(r.pathPattern()), Comparator.nullsFirst(String::compareTo)));
        SecurityRuleInput chosen = winners.get(0);

        if (winners.size() > 1) {
            // Equal specificity: usable only when every tied rule states exactly the same policy.
            SecurityPolicy reference = policyOf(chosen);
            for (SecurityRuleInput other : winners) {
                if (!samePolicy(reference, policyOf(other))) {
                    return new Selection(null, viewOf(chosen, best), true,
                            "Equally specific source security rules disagree (" + winners.size()
                                    + " rules at specificity " + best + "); conformance cannot be determined.");
                }
            }
        }
        return new Selection(policyOf(chosen), viewOf(chosen, best), false, null);
    }

    private int rank(SecurityRuleInput rule, String className, String methodName, String path) {
        String scope = upper(rule.scope());
        boolean sameClass = eq(upper(rule.className()), upper(className));
        if ("METHOD".equals(scope)) {
            if (sameClass && eq(rule.methodName(), methodName)) return RANK_METHOD;
            return Integer.MIN_VALUE;
        }
        if ("CLASS".equals(scope)) {
            return sameClass ? RANK_CLASS : Integer.MIN_VALUE;
        }
        // SECURITY_CONFIGURATION (or anything unscoped): decide by path pattern specificity.
        String pattern = rule.pathPattern() == null ? "" : rule.pathPattern().trim();
        if (pattern.isEmpty() || "**".equals(pattern) || Boolean.TRUE.equals(rule.catchAll())) {
            return RANK_CATCH_ALL;   // anyRequest() fallback
        }
        if (pattern.contains("**")) {
            List<String> literal = literalPrefix(pattern);
            return prefixMatches(literal, segments(path)) ? RANK_PATH_WILDCARD + literal.size() : Integer.MIN_VALUE;
        }
        return pathMatches(pattern, path) ? RANK_PATH_EXACT + segments(pattern).size() : Integer.MIN_VALUE;
    }
    /**
     * Translates a Step 9 rule into the shared {@link SecurityPolicy} model. A rule that cannot be
     * safely flattened (unresolved dynamic argument, complex SpEL, denyAll) stays {@code UNKNOWN},
     * which the engine reports as UNVERIFIED — never as MATCH or MISMATCH.
     */
    private SecurityPolicy policyOf(SecurityRuleInput rule) {
        if (Boolean.TRUE.equals(rule.unresolved())) return SecurityPolicy.UNKNOWN;
        if (Boolean.TRUE.equals(rule.complex())) return SecurityPolicy.UNKNOWN;
        String operator = upper(rule.operator());
        if (operator == null) return SecurityPolicy.UNKNOWN;
        return switch (operator) {
            case "PERMIT_ALL", "ANONYMOUS" -> SecurityPolicy.permitAllPolicy();
            case "AUTHENTICATED" -> rule.roles() != null && !rule.roles().isEmpty()
                    ? SecurityPolicy.roles(null, sorted(rule.roles()))
                    : SecurityPolicy.authenticated();
            case "HAS_ROLE", "HAS_ANY_ROLE", "ROLES_ALLOWED", "SECURED" ->
                    rule.roles() == null || rule.roles().isEmpty()
                            ? SecurityPolicy.UNKNOWN
                            : SecurityPolicy.roles(null, sorted(rule.roles()));
            case "HAS_AUTHORITY", "HAS_ANY_AUTHORITY" ->
                    rule.authorities() == null || rule.authorities().isEmpty()
                            ? SecurityPolicy.UNKNOWN
                            : SecurityPolicy.authorities(null, sorted(rule.authorities()));
            case "SCOPE_CHECK" -> rule.scopes() == null || rule.scopes().isEmpty()
                    ? SecurityPolicy.UNKNOWN
                    : SecurityPolicy.scopes(null, sorted(rule.scopes()));
            // DENY_ALL, bare ROLE_CHECK / AUTHORITY_CHECK, or anything unrecognized: the shared model
            // has no equivalent, so nothing can be asserted.
            default -> SecurityPolicy.UNKNOWN;
        };
    }

    private boolean samePolicy(SecurityPolicy a, SecurityPolicy b) {
        if (a == null || b == null) return a == b;
        return a.unknown() == b.unknown()
                && a.permitAll() == b.permitAll()
                && eq(a.authentication(), b.authentication())
                && sorted(a.effectiveRoles()).equals(sorted(b.effectiveRoles()))
                && sorted(a.effectiveAuthorities()).equals(sorted(b.effectiveAuthorities()))
                && sorted(a.effectiveScopes()).equals(sorted(b.effectiveScopes()));
    }

    private AppliedRule viewOf(SecurityRuleInput rule, int rank) {
        return new AppliedRule(rule.scope(), rule.operator(), rule.pathPattern(), rank,
                rule.file(), rule.lineStart());
    }

    private List<String> literalPrefix(String pattern) {
        List<String> literal = new ArrayList<>();
        for (String s : segments(pattern)) {
            if (s.contains("**") || isVariable(s)) break;
            literal.add(s);
        }
        return literal;
    }

    private boolean prefixMatches(List<String> literal, List<String> path) {
        if (path.size() < literal.size()) return false;
        for (int i = 0; i < literal.size(); i++) {
            if (!literal.get(i).equals(path.get(i))) return false;
        }
        return true;
    }

    /** Segment comparison for wildcard-free patterns; {@code {var}} matches {@code {var}} only. */
    private boolean pathMatches(String pattern, String path) {
        List<String> a = segments(pattern);
        List<String> b = segments(path);
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            String x = a.get(i);
            String y = b.get(i);
            if (isVariable(x) || isVariable(y)) {
                if (!(isVariable(x) && isVariable(y))) return false;
            } else if (!x.equals(y)) {
                return false;
            }
        }
        return true;
    }

    private boolean isVariable(String segment) {
        return segment.length() > 2 && segment.charAt(0) == '{' && segment.endsWith("}");
    }

    /** Comparison-only normalization (leading slash ensured, trailing slash dropped). */
    private List<String> segments(String path) {
        if (path == null) return List.of();
        String n = path.trim();
        while (n.endsWith("/")) n = n.substring(0, n.length() - 1);
        if (n.isEmpty()) return List.of();
        if (n.charAt(0) != '/') n = "/" + n;
        return Arrays.asList(n.substring(1).split("/", -1));
    }

    /** Deterministic ordering for role/authority/scope lists; source-declared values are preserved. */
    private List<String> sorted(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(Objects::nonNull).sorted().toList();
    }

    private String upper(String s) { return s == null ? null : s.toUpperCase(Locale.ROOT); }

    private boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }
}
