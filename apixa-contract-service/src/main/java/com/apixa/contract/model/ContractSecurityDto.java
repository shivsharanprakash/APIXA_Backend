package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Security declared by the OpenAPI contract for one scope: the whole document
 * ({@code security} at the root) or a single operation.
 *
 * <p>OpenAPI semantics are preserved rather than flattened: {@code security} is an OR-list of
 * requirement objects, and each requirement object is an AND-set of schemes. Therefore:
 * <ul>
 *   <li>{@code source} — where the effective declaration was found: {@code OPERATION}, {@code GLOBAL}
 *       (operation declares nothing, so the root declaration is inherited) or {@code NONE} (the
 *       document declares no security at all for this scope).</li>
 *   <li>{@code declared} — an explicit {@code security} array applies to this scope.</li>
 *   <li>{@code explicitlyPublic} — the effective declaration is the empty array {@code security: []},
 *       which per OpenAPI means "no security", even when a global requirement exists.</li>
 *   <li>{@code anonymousAccessAllowed} — the declaration can be satisfied without credentials
 *       (nothing declared, empty array, or an empty requirement object {@code {}} among the
 *       alternatives, which OpenAPI treats as optional/anonymous access).</li>
 *   <li>{@code requiresSecurity} — credentials are declared and no alternative is anonymous.</li>
 * </ul>
 * Absence of an operation-level declaration is therefore never reported as "public": it is reported
 * as an inherited (or absent) declaration through {@code source}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractSecurityDto(
        String source,
        boolean declared,
        boolean explicitlyPublic,
        boolean anonymousAccessAllowed,
        boolean requiresSecurity,
        List<ContractSecurityAlternativeDto> alternatives) {
}
