package com.apixa.mapping.engine;

import com.apixa.mapping.model.MappingResultDto;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Endpoint correspondence engine.
 * Matches a normalized contract endpoint to extracted Spring endpoints.
 * Ambiguity is never silently resolved: MULTIPLE_CANDIDATES and UNCERTAIN stay explicit.
 */
@Component
public class EndpointMapper {

    public record ImplEndpoint(String className, String methodName, String httpMethod, String path) {}

    private final AntPathMatcher matcher = new AntPathMatcher();

    public MappingResultDto match(String method, String path, String operationId, List<ImplEndpoint> impls) {
        List<ImplEndpoint> candidates = new ArrayList<>();
        for (ImplEndpoint impl : impls) {
            if (!methodMatches(method, impl.httpMethod())) continue;
            if (matcher.match(impl.path(), path)) candidates.add(impl);
        }
        if (candidates.isEmpty()) {
            // retry: contract paths with {vars} must match impl patterns too (they do via AntPathMatcher),
            // so empty means genuinely unmatched
            return new MappingResultDto(method, path, "UNMATCHED", null, null, null, null, null,
                    "No implementation endpoint with HTTP method " + method + " matches " + path);
        }
        if (candidates.size() > 1) {
            List<MappingResultDto.CandidateDto> dtos = candidates.stream()
                    .map(c -> new MappingResultDto.CandidateDto(c.className(), c.methodName(), c.httpMethod(), c.path()))
                    .toList();
            return new MappingResultDto(method, path, "MULTIPLE_CANDIDATES", null, null, null, null, dtos,
                    candidates.size() + " implementation endpoints match " + method + " " + path + "; ambiguity kept explicit");
        }
        ImplEndpoint chosen = candidates.get(0);
        String confidence = confidenceOf(path, chosen, operationId);
        String status = confidence.equals("LOW") ? "UNCERTAIN" : "MATCHED";
        return new MappingResultDto(method, path, status, confidence, chosen.className(), chosen.methodName(),
                chosen.path(), null, "Matched to " + chosen.className() + "." + chosen.methodName()
                + " with " + confidence + " confidence");
    }

    private boolean methodMatches(String contractMethod, String implMethod) {
        return implMethod.equalsIgnoreCase(contractMethod)
                || implMethod.equalsIgnoreCase("ANY")
                || (implMethod.contains(",") && List.of(implMethod.toUpperCase(Locale.ROOT).split(","))
                        .contains(contractMethod.toUpperCase(Locale.ROOT)));
    }

    private String confidenceOf(String contractPath, ImplEndpoint impl, String operationId) {
        if (operationId != null && impl.methodName() != null
                && operationId.equalsIgnoreCase(impl.methodName())) return "HIGH";
        boolean implHasVars = impl.path().contains("{") || impl.path().contains("*");
        boolean contractHasVars = contractPath.contains("{");
        if (impl.path().equals(contractPath)) return "HIGH";
        if (impl.path().endsWith("**")) return "LOW";
        if (implHasVars || contractHasVars) return "MEDIUM";
        return "MEDIUM";
    }
}
