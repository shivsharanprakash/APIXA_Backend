package com.apixa.contract.openapi;

import com.apixa.common.error.ApiException;
import com.apixa.common.model.SecurityPolicy;
import com.apixa.contract.model.ContractEndpointDto;
import com.apixa.contract.model.NormalizedSecurityContractDto;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Parses OpenAPI 3.x (YAML or JSON) and normalizes it into a security contract.
 * Research logic lives here, not in the controller.
 */
@Component
public class OpenApiContractParser {

    public NormalizedSecurityContractDto parse(String sourceName, String content) {
        if (content == null || content.isBlank()) {
            throw ApiException.badRequest("OpenAPI content must not be empty");
        }
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIParser().readContents(content, null, options);
        OpenAPI api = result.getOpenAPI();
        if (api == null) {
            String errors = result.getMessages() == null ? "unknown parse error"
                    : String.join("; ", result.getMessages());
            throw ApiException.badRequest("Failed to parse OpenAPI specification: " + errors);
        }

        Map<String, String> schemes = new LinkedHashMap<>();
        if (api.getComponents() != null && api.getComponents().getSecuritySchemes() != null) {
            api.getComponents().getSecuritySchemes().forEach((name, scheme) ->
                    schemes.put(name, scheme.getType() == null ? "UNKNOWN" : scheme.getType().toString()));
        }

        List<ContractEndpointDto> endpoints = new ArrayList<>();
        if (api.getPaths() != null) {
            api.getPaths().forEach((path, pathItem) -> collectOperations(path, pathItem, api, content, endpoints));
        }
        endpoints.sort(Comparator.comparing(ContractEndpointDto::path).thenComparing(ContractEndpointDto::method));

        return new NormalizedSecurityContractDto(
                UUID.randomUUID().toString(),
                api.getInfo() == null ? null : api.getInfo().getTitle(),
                api.getInfo() == null ? null : api.getInfo().getVersion(),
                api.getOpenapi(),
                sourceName,
                sha256(content),
                schemes,
                endpoints);
    }

    private void collectOperations(String path, PathItem pathItem, OpenAPI api, String content,
                                   List<ContractEndpointDto> endpoints) {
        Map<PathItem.HttpMethod, Operation> ops = new LinkedHashMap<>();
        if (pathItem.getGet() != null) ops.put(PathItem.HttpMethod.GET, pathItem.getGet());
        if (pathItem.getPost() != null) ops.put(PathItem.HttpMethod.POST, pathItem.getPost());
        if (pathItem.getPut() != null) ops.put(PathItem.HttpMethod.PUT, pathItem.getPut());
        if (pathItem.getDelete() != null) ops.put(PathItem.HttpMethod.DELETE, pathItem.getDelete());
        if (pathItem.getPatch() != null) ops.put(PathItem.HttpMethod.PATCH, pathItem.getPatch());
        ops.forEach((httpMethod, operation) -> {
            String method = httpMethod.name();
            List<SecurityRequirement> requirements = operation.getSecurity() != null
                    ? operation.getSecurity()
                    : api.getSecurity();
            SecurityPolicy policy = normalize(requirements,
                    api.getComponents() == null ? null : api.getComponents().getSecuritySchemes());
            String jsonPath = "$.paths['" + path + "']." + method.toLowerCase(Locale.ROOT)
                    + (operation.getSecurity() != null ? ".security" : " (global security)");
            endpoints.add(new ContractEndpointDto(method, path, operation.getOperationId(), policy,
                    "openapi", jsonPath));
        });
    }

    /** Normalize OpenAPI security requirements into the shared SecurityPolicy model. */
    private SecurityPolicy normalize(List<SecurityRequirement> requirements,
                                     Map<String, SecurityScheme> schemes) {
        if (requirements == null) {
            // no global and no operation-level security: open endpoint
            return SecurityPolicy.permitAll();
        }
        if (requirements.isEmpty()) {
            // explicit empty security array means the operation is public
            return SecurityPolicy.permitAll();
        }
        List<String> roles = new ArrayList<>();
        List<String> scopes = new ArrayList<>();
        String firstScheme = null;
        String authType = null;
        for (SecurityRequirement requirement : requirements) {
            for (Map.Entry<String, List<String>> entry : requirement.entrySet()) {
                SecurityScheme scheme = schemes == null ? null : schemes.get(entry.getKey());
                if (firstScheme == null) firstScheme = entry.getKey();
                if (scheme != null) {
                    authType = scheme.getType() == null ? authType : scheme.getType().toString();
                    if (scheme.getFlows() != null) {
                        scheme.getFlows().values().forEach(flow -> {
                            if (flow.getScopes() != null) scopes.addAll(flow.getScopes().keySet());
                        });
                    }
                }
                roles.addAll(entry.getValue());
            }
        }
        if (roles.isEmpty() && scopes.isEmpty()) {
            return new SecurityPolicy(authType == null ? "AUTHENTICATED" : authType, firstScheme,
                    null, null, null, false, false);
        }
        // OIDC/oauth2 entries carry scopes; http-bearer entries commonly carry roles
        if (!scopes.isEmpty()) {
            return SecurityPolicy.scopes(firstScheme, scopes);
        }
        return SecurityPolicy.roles(firstScheme, roles);
    }

    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormatHolder.hex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static final class HexFormatHolder {
        static String hex(byte[] bytes) { return java.util.HexFormat.of().formatHex(bytes); }
    }
}
