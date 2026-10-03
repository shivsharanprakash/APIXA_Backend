package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A security scheme as declared under {@code components.securitySchemes}. Ordered by scheme name in
 * the extraction result so the output is deterministic. {@code flows} lists the declared OAuth2 flow
 * types (in OpenAPI order) and {@code declaredScopes} the scopes those flows define (sorted, unique).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractSecuritySchemeDto(
        String name,
        String type,
        String httpScheme,
        String bearerFormat,
        String apiKeyIn,
        String apiKeyName,
        String openIdConnectUrl,
        List<String> flows,
        List<String> declaredScopes) {
}
