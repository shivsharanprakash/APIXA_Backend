package com.apixa.contract.model;

import com.apixa.common.model.SecurityPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Normalized security contract for one endpoint, with source traceability. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractEndpointDto(
        String method,
        String path,
        String operationId,
        SecurityPolicy expectedSecurity,
        String sourceFile,
        String jsonPath) {

    public String key() { return method + " " + path; }
}
