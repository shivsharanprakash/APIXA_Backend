package com.apixa.contract.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Step 6 OpenAPI import result: import metadata only. {@code status=IMPORTED} means the uploaded file
 * was validated as an OpenAPI document and stored; {@code extractionPerformed=false} makes explicit
 * that no endpoint/security extraction was produced by this step.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OpenApiImportResultDto(
        String id,
        String status,
        String fileName,
        String format,
        String contentType,
        long sizeBytes,
        String openapiVersion,
        String title,
        String apiVersion,
        String openapiHash,
        String importedAt,
        boolean extractionPerformed,
        String documentUrl) {
}
