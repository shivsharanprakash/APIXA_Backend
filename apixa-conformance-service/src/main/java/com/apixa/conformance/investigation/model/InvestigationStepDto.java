package com.apixa.conformance.investigation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvestigationStepDto(
        int stepNumber,
        String title,
        String toolName,
        String status,          // RUNNING, SUCCESS, WAITING_APPROVAL, FAILED, SKIPPED
        String description,
        Object inputData,
        Object outputData,
        String timestamp) {

    public static InvestigationStepDto of(int stepNumber, String title, String toolName, String status,
                                          String description, Object inputData, Object outputData) {
        return new InvestigationStepDto(stepNumber, title, toolName, status, description,
                inputData, outputData, String.valueOf(System.currentTimeMillis()));
    }
}
