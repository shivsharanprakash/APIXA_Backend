package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One declared response of an operation. {@code statusCode} is kept as the structured key the
 * document uses ({@code "200"}, {@code "401"}, {@code "403"}, {@code "2XX"}, {@code "default"});
 * responses are ordered numeric ascending, then XX ranges, then {@code default}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractResponseDto(
        String statusCode,
        String description,
        List<ContractMediaTypeDto> content) {
}
