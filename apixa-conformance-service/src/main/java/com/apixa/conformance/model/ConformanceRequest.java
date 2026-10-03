package com.apixa.conformance.model;

import com.apixa.common.model.SecurityPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Step 11 conformance request: the three already-extracted datasets, supplied explicitly.
 *
 * <p>Nothing is discovered here — no OpenAPI document is re-parsed, no Java source is re-scanned and
 * no local path is opened. The service consumes Step 7 ({@code contractEndpoints}), Step 10
 * ({@code mappings}) and Step 9 ({@code securityRules}) output, preserving the pipeline separation.
 *
 * <p>The nested input records use the same wire field names as their producing services
 * ({@code ContractEndpointDto}, {@code MappingResultDto}, {@code SecurityEvidenceDto}), so payloads
 * can be forwarded unchanged; they are transport shapes, not a second domain model.
 */
public record ConformanceRequest(
        @Valid @NotEmpty List<ContractEndpointInput> contractEndpoints,
        List<MappingInput> mappings,
        List<SecurityRuleInput> securityRules) {

    /** One Step 7 contract endpoint. Only method/path/operationId/expectedSecurity is relevant here. */
    public record ContractEndpointInput(String method, String path, String operationId,
                                        SecurityPolicy expectedSecurity) {}

    /** One Step 10 mapping result. Candidate DTOs keep class/method/path/file/line. */
    public record MappingInput(String method, String path, String status,
                               String implementationClass, String implementationMethod, String implementationPath,
                               List<CandidateInput> candidates) {

        public record CandidateInput(String className, String methodName, String httpMethod, String path,
                                     String file, Integer lineStart) {}
    }

    /** One Step 9 source security rule. Mirrors {@code SecurityEvidenceDto} field names. */
    public record SecurityRuleInput(String scope, String operator, String pathPattern,
                                    String className, String methodName, String expression,
                                    List<String> roles, List<String> authorities, List<String> scopes,
                                    Boolean complex, Boolean unresolved, Boolean catchAll,
                                    String file, Integer lineStart) {}
}