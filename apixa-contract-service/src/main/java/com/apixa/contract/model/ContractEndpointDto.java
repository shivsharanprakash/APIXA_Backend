package com.apixa.contract.model;

import com.apixa.common.model.SecurityPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Normalized record for one OpenAPI path operation, with source traceability.
 *
 * <p>The first six fields are the pre-existing normalized security contract (Step 6 and earlier).
 * The remaining fields carry the contract-level information extracted in Step 7 (contract
 * extraction): operation metadata, the security the contract <em>declares</em>, parameters, request
 * body and responses. They extend this single endpoint record instead of duplicating
 * path/method/operationId/security in a second DTO.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractEndpointDto(
        String method,
        String path,
        String operationId,
        SecurityPolicy expectedSecurity,
        String sourceFile,
        String jsonPath,
        String summary,
        String description,
        List<String> tags,
        boolean deprecated,
        ContractSecurityDto declaredSecurity,
        List<ContractParameterDto> parameters,
        ContractRequestBodyDto requestBody,
        List<ContractResponseDto> responses) {

    public String key() { return method + " " + path; }
}
