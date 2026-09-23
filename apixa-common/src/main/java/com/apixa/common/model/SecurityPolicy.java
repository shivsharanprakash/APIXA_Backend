package com.apixa.common.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Normalized security policy used across contract, source, conformance, impact and runtime services. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SecurityPolicy(
        String authentication,      // NONE, AUTHENTICATED, API_KEY, BEARER, OAUTH2, OPENID, HTTP_BASIC, CUSTOM
        String schemeName,
        List<String> roles,
        List<String> authorities,
        List<String> scopes,
        boolean permitAll,
        boolean unknown) {

    public static final SecurityPolicy UNKNOWN = new SecurityPolicy(null, null, null, null, null, false, true);

    public static SecurityPolicy permitAll() {
        return new SecurityPolicy("NONE", null, null, null, null, true, false);
    }

    public static SecurityPolicy authenticated() {
        return new SecurityPolicy("AUTHENTICATED", null, null, null, null, false, false);
    }

    public static SecurityPolicy roles(String scheme, List<String> roles) {
        return new SecurityPolicy(scheme == null ? "AUTHENTICATED" : scheme, scheme, roles, null, null, false, false);
    }

    public static SecurityPolicy authorities(String scheme, List<String> authorities) {
        return new SecurityPolicy(scheme == null ? "AUTHENTICATED" : scheme, scheme, null, authorities, null, false, false);
    }

    public static SecurityPolicy scopes(String scheme, List<String> scopes) {
        return new SecurityPolicy("OAUTH2", scheme, null, null, scopes, false, false);
    }

    public List<String> effectiveRoles() { return roles == null ? List.of() : roles; }
    public List<String> effectiveAuthorities() { return authorities == null ? List.of() : authorities; }
    public List<String> effectiveScopes() { return scopes == null ? List.of() : scopes; }
    public boolean requiresAuthentication() { return !permitAll && !unknown; }
}
