package com.apixa.mapping.engine;

import com.apixa.mapping.model.MappingResultDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Endpoint correspondence engine (Step 10 endpoint mapping).
 *
 * <p>Matches each contract endpoint (OpenAPI, Step 7) against the Spring source endpoints already
 * extracted by the source analysis service (Step 8). The pre-existing algorithm and all of its
 * statuses are preserved: {@code MATCHED}, {@code MULTIPLE_CANDIDATES}, {@code UNMATCHED} and
 * {@code UNCERTAIN}.
 *
 * <p>Step 10 decides <em>endpoint correspondence only</em>. It never looks at any security
 * information (Step 9) and never compares policies (Step 11).
 *
 * <p>Matching is deliberately <b>segment based</b> rather than wildcard/regex based:
 * <ol>
 *   <li>the HTTP method must agree ({@code ANY} in the source means Spring declared no method, and
 *       {@code METHOD1,METHOD2} lists are expanded);</li>
 *   <li>each path is normalized <em>for comparison only</em> — a leading slash is ensured and
 *       trailing slashes are removed, so {@code /users} and {@code /users/} are the same route;</li>
 *   <li>the two paths must have the same segment count;</li>
 *   <li>a {@code {variable}} segment (optionally {@code {name:regex}} such as {@code {filename:.+}})
 *       on one side matches a variable segment on the other side whatever the variable names are,
 *       so {@code /users/{id}} equals {@code /users/{userId}};</li>
 *   <li>a variable segment never matches a static segment, so {@code /users/{id}} does not match
 *       {@code /users/all};</li>
 *   <li>every other segment must be textually equal (case sensitive, as HTTP paths are).</li>
 * </ol>
 * A trailing {@code **} wildcard is still honoured, but a correspondence that relies on it is
 * reported as {@code UNCERTAIN} instead of {@code MATCHED}, because the matcher cannot safely prove
 * that such a route is the intended one. No user supplied text is ever compiled into a regex.
 *
 * <p>Ambiguity is never silently resolved: every candidate is collected and kept, and a contract
 * endpoint with more than one source candidate is reported as {@code MULTIPLE_CANDIDATES}. The
 * original (never normalized) paths are always what the response reports.
 */
@Component
public class EndpointMapper {

    /**
     * One already extracted Spring source endpoint (Step 8 output). {@code file} and
     * {@code lineStart} are the Step 8 source evidence, carried through unchanged so a mapping keeps
     * pointing at the exact declaration.
     */
    public record ImplEndpoint(String className, String methodName, String httpMethod, String path,
                               String file, Integer lineStart) {

        /** Backwards-compatible constructor for pre-Step-10 call sites without source evidence. */
        public ImplEndpoint(String className, String methodName, String httpMethod, String path) {
            this(className, methodName, httpMethod, path, null, null);
        }
    }

    public MappingResultDto match(String method, String path, String operationId, List<ImplEndpoint> impls) {
        List<ImplEndpoint> candidates = new ArrayList<>();
        boolean wildcardMatch = false;
        for (ImplEndpoint impl : impls) {
            if (!methodMatches(method, impl.httpMethod())) continue;
            MatchOutcome outcome = pathsMatch(path, impl.path());
            if (outcome == MatchOutcome.NONE) continue;
            candidates.add(impl);
            wildcardMatch |= outcome == MatchOutcome.WILDCARD;
        }
        candidates.sort(Comparator.comparing(ImplEndpoint::path, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ImplEndpoint::httpMethod, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ImplEndpoint::file, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ImplEndpoint::lineStart, Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(ImplEndpoint::className, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ImplEndpoint::methodName, Comparator.nullsFirst(String::compareTo)));

        if (candidates.isEmpty()) {
            // No source endpoint satisfies method + path structure: genuinely unmatched, and never a
            // security finding — that verdict belongs to Step 11.
            return new MappingResultDto(method, path, "UNMATCHED", null, null, null, null, List.of(),
                    "No implementation endpoint with HTTP method " + method + " matches " + path);
        }
        if (candidates.size() > 1) {
            return new MappingResultDto(method, path, "MULTIPLE_CANDIDATES", null, null, null, null,
                    candidateDtos(candidates), candidates.size() + " implementation endpoints match "
                            + method + " " + path + "; ambiguity kept explicit");
        }
        ImplEndpoint chosen = candidates.get(0);
        String confidence = wildcardMatch ? "LOW" : confidenceOf(path, chosen, operationId);
        String status = "LOW".equals(confidence) ? "UNCERTAIN" : "MATCHED";
        return new MappingResultDto(method, path, status, confidence, chosen.className(), chosen.methodName(),
                chosen.path(), candidateDtos(candidates), "Matched to " + chosen.className() + "."
                        + chosen.methodName() + " with " + confidence + " confidence");
    }

    private List<MappingResultDto.CandidateDto> candidateDtos(List<ImplEndpoint> candidates) {
        return candidates.stream().map(c -> new MappingResultDto.CandidateDto(c.className(), c.methodName(),
                c.httpMethod(), c.path(), c.file(), c.lineStart())).toList();
    }

    private enum MatchOutcome { NONE, EXACT, WILDCARD }

    /** Segment comparison of the two paths. Pure string work: nothing is compiled into a regex. */
    private MatchOutcome pathsMatch(String contractPath, String sourcePath) {
        if (contractPath == null || sourcePath == null) return MatchOutcome.NONE;
        List<String> contractSegments = segments(contractPath);
        List<String> sourceSegments = segments(sourcePath);
        boolean wildcard = false;
        int i = 0;
        while (i < contractSegments.size() && i < sourceSegments.size()) {
            String contractSegment = contractSegments.get(i);
            String sourceSegment = sourceSegments.get(i);
            // a "**" on either side spans exactly the remaining segments; such a correspondence is
            // only a weak hint, so the caller downgrades it to UNCERTAIN
            // A "**" on either side is honoured ONLY when it spans exactly the remaining segments.
            // It is never allowed to absorb one or several concrete segments, so a broad wildcard
            // cannot swallow "/api/users/all" or "/api/users/{id}" and turn them into a match.
            boolean contractSpans = "**".equals(contractSegment)
                    && contractSegments.size() - i == sourceSegments.size() - i;
            boolean sourceSpans = "**".equals(sourceSegment)
                    && sourceSegments.size() - i == contractSegments.size() - i;
            if (contractSpans || sourceSpans) {
                wildcard = true;
                break;
            }
            if (!segmentsMatch(contractSegment, sourceSegment)) return MatchOutcome.NONE;
            i++;
        }
        if (wildcard) return MatchOutcome.WILDCARD;
        // without a wildcard every segment must have been consumed on both sides
        return i == contractSegments.size() && i == sourceSegments.size()
                ? MatchOutcome.EXACT : MatchOutcome.NONE;
    }

    /** A variable segment matches a variable segment (names may differ); static must equal static. */
    private boolean segmentsMatch(String contractSegment, String sourceSegment) {
        if (isVariable(contractSegment) || isVariable(sourceSegment)) {
            return isVariable(contractSegment) && isVariable(sourceSegment);
        }
        return contractSegment.equals(sourceSegment);
    }

    /** {@code {id}} or {@code {filename:.+}} — the name and any inline regex are irrelevant here. */
    private boolean isVariable(String segment) {
        return segment.length() > 2 && segment.charAt(0) == '{' && segment.endsWith("}");
    }

    /** Comparison-only normalization: leading slash ensured, trailing slashes dropped. */
    private List<String> segments(String path) {
        String normalized = path.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) return List.of();
        if (normalized.charAt(0) != '/') normalized = "/" + normalized;
        return Arrays.asList(normalized.substring(1).split("/", -1));
    }

    private boolean methodMatches(String contractMethod, String implMethod) {
        return implMethod.equalsIgnoreCase(contractMethod)
                || implMethod.equalsIgnoreCase("ANY")
                || (implMethod.contains(",") && List.of(implMethod.toUpperCase(Locale.ROOT).split(","))
                        .contains(contractMethod.toUpperCase(Locale.ROOT)));
    }

    /** operationId is metadata only: it may raise confidence but is never required to match. */
    private String confidenceOf(String contractPath, ImplEndpoint impl, String operationId) {
        if (operationId != null && impl.methodName() != null
                && operationId.equalsIgnoreCase(impl.methodName())) return "HIGH";
        if (contractPath != null && contractPath.equals(impl.path())) return "HIGH";
        String implPath = impl.path() == null ? "" : impl.path();
        while (implPath.endsWith("/")) implPath = implPath.substring(0, implPath.length() - 1);
        if ("**".equals(implPath) || implPath.endsWith("/**")) return "LOW";
        // otherwise the two paths differ only by variable names: structurally equivalent
        return "MEDIUM";
    }
}
