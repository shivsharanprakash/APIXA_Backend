package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** The request body declared for an operation, with its media types (ordered by media type name). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractRequestBodyDto(
        boolean required,
        List<ContractMediaTypeDto> content) {
}
