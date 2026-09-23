package com.apixa.sourceanalysis.model;

import com.apixa.common.model.SecurityPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceEndpointDto(
        String className,
        String methodName,
        String httpMethod,
        String path,
        String file,
        Integer lineStart,
        SecurityPolicy implementedSecurity,
        List<SecurityEvidenceDto> evidence) {

    public String key() { return httpMethod + " " + path; }
}
