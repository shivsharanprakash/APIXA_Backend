package com.apixa.conformance.engine;

import com.apixa.common.model.SecurityPolicy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Core static conformance engine: compares expected security (contract)
 * against implemented security (source analysis).
 * Results: MATCH, MISMATCH, PARTIAL, UNVERIFIED - always with a reason.
 */
@Component
public class SecurityConformanceEngine {

    public enum Result { MATCH, MISMATCH, PARTIAL, UNVERIFIED }

    public record Finding(Result result, String expected, String implemented, String reason) {}

    public Finding compare(SecurityPolicy expected, SecurityPolicy implemented) {
        if (implemented == null || implemented.unknown()) {
            return new Finding(Result.UNVERIFIED, describe(expected), describe(implemented),
                    "No sufficient implementation evidence found for this endpoint.");
        }
        if (expected == null || expected.unknown()) {
            return new Finding(Result.UNVERIFIED, describe(expected), describe(implemented),
                    "No security requirement declared in the contract for this endpoint.");
        }

        boolean expectedOpen = expected.permitAll() || "NONE".equals(expected.authentication());
        boolean implOpen = implemented.permitAll() || "NONE".equals(implemented.authentication());

        if (expectedOpen && implOpen) {
            return new Finding(Result.MATCH, describe(expected), describe(implemented),
                    "Both contract and implementation treat the endpoint as public.");
        }
        if (expectedOpen && !implOpen) {
            return new Finding(Result.MISMATCH, describe(expected), describe(implemented),
                    "The contract declares a public endpoint, but the implementation requires authentication/authorization.");
        }
        if (!expectedOpen && implOpen) {
            return new Finding(Result.MISMATCH, describe(expected), describe(implemented),
                    "The contract requires authentication, but the implementation permits anonymous access (e.g. permitAll()).");
        }

        // both require authentication; compare authorization strength
        Set<String> expectedRoles = new LinkedHashSet<>(expected.effectiveRoles());
        Set<String> implRoles = new LinkedHashSet<>(implemented.effectiveRoles());
        Set<String> expectedAuth = new LinkedHashSet<>(expected.effectiveAuthorities());
        Set<String> implAuth = new LinkedHashSet<>(implemented.effectiveAuthorities());
        Set<String> expectedScopes = new LinkedHashSet<>(expected.effectiveScopes());
        Set<String> implScopes = new LinkedHashSet<>(implemented.effectiveScopes());

        boolean expectedStrong = !expectedRoles.isEmpty() || !expectedAuth.isEmpty() || !expectedScopes.isEmpty();
        boolean implStrong = !implRoles.isEmpty() || !implAuth.isEmpty() || !implScopes.isEmpty();

        if (expectedStrong && implStrong) {
            boolean roleMatch = equalIgnoringScheme(expectedRoles, implRoles)
                    || equalIgnoringScheme(expectedRoles, implAuth)
                    || equalIgnoringScheme(expectedAuth, implRoles);
            boolean scopeMatch = equalIgnoringScheme(expectedScopes, implScopes);
            if (roleMatch || scopeMatch) {
                return new Finding(Result.MATCH, describe(expected), describe(implemented),
                        "Contract and implementation enforce the same authorization requirement.");
            }
            boolean implWeaker = implRoles.isEmpty() && implAuth.isEmpty() && implScopes.isEmpty();
            if (!implWeaker) {
                return new Finding(Result.MISMATCH, describe(expected), describe(implemented),
                        "Contract requires " + join(expectedRoles, expectedAuth, expectedScopes)
                                + " authorization, while the implementation requires "
                                + join(implRoles, implAuth, implScopes) + ".");
            }
        }
        if (expectedStrong && !implStrong) {
            return new Finding(Result.PARTIAL, describe(expected), describe(implemented),
                    "Contract requires " + join(expectedRoles, expectedAuth, expectedScopes)
                            + " authorization, while the implementation only requires authentication.");
        }
        if (!expectedStrong && implStrong) {
            return new Finding(Result.PARTIAL, describe(expected), describe(implemented),
                    "The implementation enforces stricter authorization ("
                            + join(implRoles, implAuth, implScopes) + ") than the contract declares.");
        }
        return new Finding(Result.MATCH, describe(expected), describe(implemented),
                "Both contract and implementation require authentication only.");
    }

    private boolean equalIgnoringScheme(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        Set<String> norm = new LinkedHashSet<>();
        for (String s : b) norm.add(s.startsWith("ROLE_") ? s.substring(5) : s);
        Set<String> normA = new LinkedHashSet<>();
        for (String s : a) normA.add(s.startsWith("ROLE_") ? s.substring(5) : s);
        return normA.equals(norm);
    }

    private String join(Set<String> roles, Set<String> authorities, Set<String> scopes) {
        List<String> parts = new ArrayList<>();
        if (!roles.isEmpty()) parts.add("roles " + roles);
        if (!authorities.isEmpty()) parts.add("authorities " + authorities);
        if (!scopes.isEmpty()) parts.add("scopes " + scopes);
        return parts.isEmpty() ? "authentication only" : String.join(", ", parts);
    }

    private String describe(SecurityPolicy p) {
        if (p == null || p.unknown()) return "UNKNOWN";
        if (p.permitAll() || "NONE".equals(p.authentication())) return "PUBLIC";
        String s = p.authentication() == null ? "AUTHENTICATED" : p.authentication();
        List<String> extra = new ArrayList<>();
        extra.addAll(p.effectiveRoles()); extra.addAll(p.effectiveAuthorities()); extra.addAll(p.effectiveScopes());
        return extra.isEmpty() ? s : s + " " + extra;
    }
}
