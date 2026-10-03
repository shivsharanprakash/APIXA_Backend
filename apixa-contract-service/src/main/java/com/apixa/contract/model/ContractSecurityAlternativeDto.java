package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One alternative of an OpenAPI {@code security} array. Alternatives are OR-ed: a request is
 * authorized when it satisfies any single alternative. The schemes inside one alternative are
 * AND-ed (all listed schemes must be satisfied), which is why they are preserved as a set instead of
 * being flattened into one list of schemes.
 *
 * <p>{@code anonymous = true} represents an empty requirement object {@code {}} - an alternative that
 * needs no credentials at all. {@code schemes} is ordered by scheme name for determinism.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractSecurityAlternativeDto(
        boolean anonymous,
        List<ContractSecuritySchemeRefDto> schemes) {
}
