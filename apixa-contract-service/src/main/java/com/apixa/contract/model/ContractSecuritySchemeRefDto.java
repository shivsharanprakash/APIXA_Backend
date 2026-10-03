package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A security scheme referenced by a requirement entry, resolved against
 * {@code components.securitySchemes} where the document defines it.
 *
 * <p>{@code requiredScopes} are the scopes/tokens the requirement lists for this scheme (OAuth2 and
 * OpenID Connect scopes, or the token list of other schemes). {@code schemeDefined=false} means the
 * document referenced a scheme that is not declared in {@code components.securitySchemes} - reported
 * as-is instead of guessing. Only OAuth2/OpenID scopes are data here; no role or authority is
 * inferred from names.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractSecuritySchemeRefDto(
        String name,
        String type,
        String httpScheme,
        String bearerFormat,
        String apiKeyIn,
        String apiKeyName,
        String openIdConnectUrl,
        List<String> requiredScopes,
        boolean schemeDefined) {
}
