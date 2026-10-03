package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One media type of a request body or response, with the schema information the document states at
 * that level (type and/or {@code $ref} only - no JSON Schema traversal).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractMediaTypeDto(
        String mediaType,
        String schemaType,
        String schemaRef) {
}
