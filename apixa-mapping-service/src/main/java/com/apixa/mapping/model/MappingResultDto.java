package com.apixa.mapping.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Result of mapping one contract endpoint (Step 7) onto the Spring source endpoints extracted by
 * Step 8. Field names and statuses are the pre-existing ones and are kept for compatibility.
 *
 * <p>Step 10 produces <b>endpoint correspondence only</b>: {@code status} is one of
 * {@code MATCHED} (exactly one source endpoint), {@code MULTIPLE_CANDIDATES} (more than one, all
 * preserved), {@code UNMATCHED} (none) or {@code UNCERTAIN} (matched only through a trailing
 * {@code **} wildcard). It is never a security conformance verdict — no MATCH/MISMATCH/PARTIAL/
 * UNVERIFIED is produced here (Step 11), and no security information is read at all.
 *
 * <p>{@code method}/{@code path} and {@code candidates[].path} are always the ORIGINAL paths exactly
 * as the contract and the source declared them. Path normalization happens only inside the
 * comparison and never mutates what is reported.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MappingResultDto(
        String method,
        String path,
        String status,           // MATCHED, MULTIPLE_CANDIDATES, UNMATCHED, UNCERTAIN
        String confidence,       // HIGH, MEDIUM, LOW
        String implementationClass,
        String implementationMethod,
        String implementationPath,
        List<CandidateDto> candidates,
        String reason) {

    /**
     * One candidate source endpoint with its Step 8 source evidence ({@code file}/{@code
     * lineStart} are added in Step 10). Never collapsed: for {@code MULTIPLE_CANDIDATES} every
     * candidate is returned.
     */
    public record CandidateDto(String className, String methodName, String httpMethod, String path,
                               String file, Integer lineStart) {

        /** Backwards-compatible constructor for pre-Step-10 call sites without source evidence. */
        public CandidateDto(String className, String methodName, String httpMethod, String path) {
            this(className, methodName, httpMethod, path, null, null);
        }
    }
}
