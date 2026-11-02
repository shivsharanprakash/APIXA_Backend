package com.apixa.conformance.investigation.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * OpenRouter AI Client for APIXA Security Investigation Agent.
 * Connects to OpenRouter's free tier models (e.g. openrouter/free, qwen/qwen-2.5-coder-32b-instruct:free)
 * with robust, zero-failure fallback to the deterministic investigation engine.
 */
@Component
public class OpenRouterClient {

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    public static final String DEFAULT_FREE_MODEL = "openrouter/free";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${apixa.investigation.openrouter.api-key:#{null}}")
    private String configuredApiKey;

    @Value("${apixa.investigation.openrouter.model:openrouter/free}")
    private String defaultModel;

    public record LlmInvestigationResponse(
            String modelUsed,
            String rootCause,
            String testRationale,
            String patchDiff,
            String regressionTestCode,
            List<String> recommendations,
            boolean success) {}

    public OpenRouterClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public Optional<LlmInvestigationResponse> queryInvestigation(
            String userApiKey,
            String requestedModel,
            String method,
            String path,
            String verdict,
            String contractRequirement,
            String implementationPolicy,
            String sourceFile,
            Integer sourceLine) {

        String apiKey = (userApiKey != null && !userApiKey.isBlank())
                ? userApiKey
                : (configuredApiKey != null && !configuredApiKey.isBlank()
                    ? configuredApiKey
                    : System.getenv("OPENROUTER_API_KEY"));

        if (apiKey == null || apiKey.isBlank()) {
            // No API key provided; gracefully skip LLM call and let deterministic engine handle
            return Optional.empty();
        }

        String model = (requestedModel != null && !requestedModel.isBlank())
                ? requestedModel
                : (defaultModel != null && !defaultModel.isBlank() ? defaultModel : DEFAULT_FREE_MODEL);

        try {
            String systemPrompt = "You are APIXA Security Investigation Agent, a principal Spring Security and OpenAPI 3.x architect. "
                    + "Analyze the security conformance mismatch between declared OpenAPI contracts and Spring Security implementation. "
                    + "Respond ONLY with a valid JSON object with the following keys: "
                    + "\"rootCause\" (string), \"testRationale\" (string), \"patchDiff\" (unified diff string), "
                    + "\"regressionTestCode\" (Java MockMvc test code string), \"recommendations\" (array of strings).";

            String userContent = String.format(
                    "Endpoint: %s %s\n"
                    + "Official Verdict: %s\n"
                    + "Declared OpenAPI Contract: %s\n"
                    + "Implemented Spring Policy: %s\n"
                    + "Source Location: %s (line %d)\n\n"
                    + "Investigate this security mismatch. Detail why it represents an authorization risk, "
                    + "how to probe it with a targeted test, the exact unified diff patch for %s, and a MockMvc regression test.",
                    method, path, verdict, contractRequirement, implementationPolicy,
                    sourceFile, sourceLine != null ? sourceLine : 40, sourceFile);

            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("model", model);
            reqBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userContent)
            ));
            reqBody.put("temperature", 0.2);

            String jsonPayload = objectMapper.writeValueAsString(reqBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENROUTER_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://github.com/shivsharanprakash/APIXA_Backend")
                    .header("X-Title", "APIXA Security Investigation Agent")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    String content = choices.get(0).path("message").path("content").asText();
                    return parseLlmOutput(content, model);
                }
            }
        } catch (Exception ex) {
            // Network failure or rate limit: return empty so deterministic fallback takes over cleanly
        }

        return Optional.empty();
    }

    private Optional<LlmInvestigationResponse> parseLlmOutput(String content, String model) {
        if (content == null || content.isBlank()) return Optional.empty();
        try {
            // Strip markdown code fences if present
            String cleaned = content.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            JsonNode node = objectMapper.readTree(cleaned);
            String rootCause = node.path("rootCause").asText(null);
            String testRationale = node.path("testRationale").asText(null);
            String patchDiff = node.path("patchDiff").asText(null);
            String regressionTestCode = node.path("regressionTestCode").asText(null);

            List<String> recommendations = new ArrayList<>();
            JsonNode recs = node.path("recommendations");
            if (recs.isArray()) {
                recs.forEach(r -> recommendations.add(r.asText()));
            }

            if (rootCause != null && !rootCause.isBlank()) {
                return Optional.of(new LlmInvestigationResponse(
                        model,
                        rootCause,
                        testRationale,
                        patchDiff,
                        regressionTestCode,
                        recommendations.isEmpty() ? null : recommendations,
                        true
                ));
            }
        } catch (Exception ex) {
            // If LLM returned free text rather than JSON, extract as root cause
            return Optional.of(new LlmInvestigationResponse(
                    model,
                    content,
                    null,
                    null,
                    null,
                    null,
                    true
            ));
        }
        return Optional.empty();
    }
}
