package com.apixa.sourceanalysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Structured evidence extracted from source: one security rule with its location.
 *
 * <p>Step 9 (source security analysis) fills the additive fields: {@code scope}
 * ({@code SECURITY_CONFIGURATION}, {@code CLASS} or {@code METHOD}), {@code operator}
 * ({@code HAS_ROLE}, {@code HAS_ANY_ROLE}, {@code HAS_AUTHORITY}, {@code HAS_ANY_AUTHORITY},
 * {@code PERMIT_ALL}, {@code AUTHENTICATED}, {@code ANONYMOUS}, {@code DENY_ALL}, {@code SECURED},
 * {@code ROLES_ALLOWED}), {@code expression} (original security expression where one exists),
 * {@code className}/{@code methodName} (declaring element for annotation rules), {@code complex}
 * (expression carries extra SpEL beyond the recognized call, so it is not flattened) and
 * {@code unresolved} (a role/authority argument was not a compile-time literal).
 * Catch-all {@code anyRequest()} rules keep {@code pathPattern = "**"}; no path is invented.
 * The pre-existing full analysis leaves the additive fields {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SecurityEvidenceDto(
        String file,
        Integer lineStart,
        Integer lineEnd,
        String ruleType,      // ROLE_CHECK, AUTHORITY_CHECK, PERMIT_ALL, AUTHENTICATED, METHOD_SECURITY, SCOPE_CHECK
        String pathPattern,
        List<String> roles,
        List<String> authorities,
        List<String> scopes,
        String sourceSnippet,
        String scope,
        String operator,
        String expression,
        String className,
        String methodName,
        Boolean complex,
        Boolean unresolved,
        Boolean catchAll) {

    /** Backwards-compatible constructor for pre-Step-9 call sites (legacy FULL pass). */
    public SecurityEvidenceDto(String file, Integer lineStart, Integer lineEnd, String ruleType,
            String pathPattern, List<String> roles, List<String> authorities,
            List<String> scopes, String sourceSnippet) {
        this(file, lineStart, lineEnd, ruleType, pathPattern, roles, authorities, scopes, sourceSnippet,
                null, null, null, null, null, null, null, null);
    }
}
