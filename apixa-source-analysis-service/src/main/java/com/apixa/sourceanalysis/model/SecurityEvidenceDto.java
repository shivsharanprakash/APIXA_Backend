package com.apixa.sourceanalysis.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Structured evidence extracted from source: one security rule with its location. */
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
        String sourceSnippet) {
}
