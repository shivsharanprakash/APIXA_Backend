package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One operation/path parameter declared by the contract. {@code required} is exactly what the
 * document declares (nothing is coerced). {@code schemaType}/{@code itemsType}/{@code schemaRef}
 * carry the shallow schema information the document states; no JSON Schema analysis is performed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractParameterDto(
        String name,
        String in,
        boolean required,
        String description,
        String schemaType,
        String schemaRef,
        String itemsType,
        List<String> enumValues) {
}
