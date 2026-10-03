package com.apixa.contract.model;

/**
 * The OpenAPI document exactly as it was imported, kept in memory alongside the normalized contract
 * (the same storage design as {@link NormalizedSecurityContractDto}) so the original file can be
 * retrieved. Addressed by the id of the normalized contract created from the same import.
 */
public record ImportedOpenApiDocument(
        String id,
        String fileName,
        String contentType,
        String format,
        long sizeBytes,
        String openapiVersion,
        String title,
        String apiVersion,
        String openapiHash,
        String importedAt,
        String content) {
}
