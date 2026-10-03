package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Step 7 contract extraction result: the contract information the imported OpenAPI document
 * <em>declares</em>, projected out of the document into structured data (never a raw parser object).
 *
 * <p>{@code status=EXTRACTED} plus {@code extractionPerformed=true} distinguish this from the Step 6
 * import result ({@code status=IMPORTED}, {@code extractionPerformed=false}), which carries import
 * metadata only. Extraction only reports what the OpenAPI contract states - no Java source is
 * inspected and no Spring roles/authorities are inferred.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContractExtractionResultDto(
        String contractId,
        String status,
        boolean extractionPerformed,
        String sourceFile,
        String openapiVersion,
        String openapiHash,
        String title,
        String apiVersion,
        int endpointCount,
        int securitySchemeCount,
        ContractSecurityDto globalSecurity,
        List<ContractSecuritySchemeDto> securitySchemes,
        List<ContractEndpointDto> endpoints) {
}
