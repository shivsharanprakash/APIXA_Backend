package com.apixa.conformance.investigation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RemediationDto(
        String summary,
        String explanation,
        String targetFile,
        Integer targetLine,
        String originalSnippet,
        String remediatedSnippet,
        String patchDiff,
        String regressionTestCode,
        List<String> preventiveRecommendations,
        String simulatedVerdictAfterFix) {
}
