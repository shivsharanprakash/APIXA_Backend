package com.apixa.conformance.investigation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApproveTestRequest(
        boolean approved,
        String targetBaseUrl,
        String customAuthHeader) {
}
