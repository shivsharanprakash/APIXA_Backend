package com.apixa.sourceanalysis.model;

import com.apixa.common.model.SecurityPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * One HTTP endpoint declared by Spring MVC/WebFlux controller source (Step 8 source endpoint analysis).
 *
 * <p>Field naming is the pre-existing one and is kept for compatibility:
 * <ul>
 *   <li>{@code className} = controller class simple name</li>
 *   <li>{@code httpMethod} = HTTP method ({@code GET}/{@code POST}/... , or {@code ANY} for a
 *       {@code @RequestMapping} with no {@code method} attribute, which Spring maps for all
 *       standard methods, or {@code UNRESOLVED} when the attribute could not be read from source)</li>
 *   <li>{@code file} = absolute Java source file path</li>
 *   <li>{@code lineStart} = 1-based line of the method-level mapping annotation (the method declaration
 *       line when the annotation has no position)</li>
 * </ul>
 *
 * <p>{@code implementedSecurity} and {@code evidence} are filled only by the pre-existing full
 * analysis. The Step 8 endpoints-only analysis leaves them {@code null} — source security analysis
 * belongs to Step 9.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceEndpointDto(
        String className,
        String methodName,
        String httpMethod,
        String path,
        String file,
        Integer lineStart,
        SecurityPolicy implementedSecurity,
        List<SecurityEvidenceDto> evidence,
        String controllerPackage,
        Integer parameterCount,
        Boolean pathResolved,
        List<String> unresolvedExpressions) {

    public String key() { return httpMethod + " " + path; }
}
