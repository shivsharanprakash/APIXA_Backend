package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NormalizedSecurityContractDto(
        String id,
        String title,
        String version,
        String openapiVersion,
        String sourceFile,
        String openapiHash,
        Map<String, String> securitySchemes,
        List<ContractEndpointDto> endpoints) {
}
