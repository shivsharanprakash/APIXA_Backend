package com.apixa.contract.openapi;

import com.apixa.common.error.ApiException;
import com.apixa.common.model.SecurityPolicy;
import com.apixa.contract.model.ContractEndpointDto;
import com.apixa.contract.model.ContractExtractionResultDto;
import com.apixa.contract.model.ContractMediaTypeDto;
import com.apixa.contract.model.ContractParameterDto;
import com.apixa.contract.model.ContractRequestBodyDto;
import com.apixa.contract.model.ContractResponseDto;
import com.apixa.contract.model.ContractSecurityAlternativeDto;
import com.apixa.contract.model.ContractSecurityDto;
import com.apixa.contract.model.ContractSecuritySchemeDto;
import com.apixa.contract.model.ContractSecuritySchemeRefDto;
import com.apixa.contract.model.NormalizedSecurityContractDto;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Parses OpenAPI 3.x (YAML or JSON) and normalizes it into a security contract.
 * Research logic lives here, not in the controller.
 */
@Component
public class OpenApiContractParser {

    /**
     * Required version declaration of an OpenAPI (3.x) or Swagger (2.x) document. Needed because the
     * configured parser silently defaults a missing version instead of reporting an error.
     */
    private static final Pattern VERSION_DECLARATION =
            Pattern.compile("[\"']?(openapi|swagger)[\"']?\\s*:\\s*[\"']?\\d+\\.\\d+");

    /** Fixed OpenAPI 3 HTTP method extraction order (endpoints are finally sorted by path then method). */
    private static final List<PathItem.HttpMethod> METHOD_ORDER = List.of(PathItem.HttpMethod.GET,
            PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH,
            PathItem.HttpMethod.DELETE, PathItem.HttpMethod.HEAD, PathItem.HttpMethod.OPTIONS,
            PathItem.HttpMethod.TRACE);

    /** Parameter location order used for deterministic output: path, query, header, cookie. */
    private static final List<String> PARAMETER_IN_ORDER = List.of("path", "query", "header", "cookie");

    private static final String SOURCE_KIND_OPENAPI = "openapi";
    private static final String STATUS_EXTRACTED = "EXTRACTED";
    private static final String SOURCE_OPERATION = "OPERATION";
    private static final String SOURCE_GLOBAL = "GLOBAL";
    private static final String SOURCE_NONE = "NONE";

    /** Existing behaviour: parse any document the configured swagger-parser accepts. */
    public NormalizedSecurityContractDto parse(String sourceName, String content) {
        return normalize(readOpenApi(content), sourceName, content);
    }

    /**
     * Import-time parsing (Step 6): the document must be parseable AND contain the mandatory OpenAPI
     * elements (openapi, info, paths). Only validation/import metadata is produced here - no endpoint or
     * security extraction is added by this method.
     */
    public NormalizedSecurityContractDto parseImported(String sourceName, String content) {
        OpenAPI api = readOpenApi(content);
        if (!VERSION_DECLARATION.matcher(content).find()) {
            // the parser silently defaults a missing version, so the declaration is checked explicitly
            throw ApiException.badRequest("Not a valid OpenAPI document: missing required 'openapi' version field");
        }
        if (api.getOpenapi() == null || api.getOpenapi().isBlank()) {
            throw ApiException.badRequest("Not a valid OpenAPI document: missing required 'openapi' version field");
        }
        if (api.getInfo() == null) {
            throw ApiException.badRequest("Not a valid OpenAPI document: missing required 'info' object");
        }
        if (api.getPaths() == null || api.getPaths().isEmpty()) {
            throw ApiException.badRequest("Not a valid OpenAPI document: missing required 'paths' object");
        }
        return normalize(api, sourceName, content);
    }

    /** Reads YAML/JSON with the swagger-parser already configured for this module. */
    private OpenAPI readOpenApi(String content) {
        if (content == null || content.isBlank()) {
            throw ApiException.badRequest("OpenAPI content must not be empty");
        }
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result;
        try {
            result = new OpenAPIParser().readContents(content, null, options);
        } catch (Exception ex) {
            // malformed input is a client error; the stack trace is never exposed
            throw ApiException.badRequest("Failed to parse OpenAPI specification: " + ex.getMessage());
        }
        OpenAPI api = result.getOpenAPI();
        if (api == null) {
            String errors = result.getMessages() == null ? "unknown parse error"
                    : String.join("; ", result.getMessages());
            throw ApiException.badRequest("Failed to parse OpenAPI specification: " + errors);
        }
        return api;
    }

    /** Normalizes an already parsed document into the shared contract model (pre-existing logic). */
    private NormalizedSecurityContractDto normalize(OpenAPI api, String sourceName, String content) {
        return new NormalizedSecurityContractDto(
                UUID.randomUUID().toString(),
                title(api),
                version(api),
                api.getOpenapi(),
                sourceName,
                sha256(content),
                schemeTypes(api),
                endpointsOf(api));
    }

    /**
     * Step 7 contract extraction: projects what the stored document *declares* for the contract
     * (metadata, declared security, endpoints with operation info, parameters, request body and
     * responses) into structured DTOs - never a raw parser object. Read-only and computed on demand
     * from the imported document, so no persistence is introduced. It reuses the unchanged
     * pre-existing security normalization ({@link SecurityPolicy}) and the same endpoint collection as
     * {@link #normalize(OpenAPI, String, String)}, so no working extraction logic is duplicated.
     */
    public ContractExtractionResultDto extract(String contractId, String sourceName, String content) {
        OpenAPI api = readOpenApi(content);
        List<ContractEndpointDto> endpoints = endpointsOf(api);
        List<ContractSecuritySchemeDto> schemes = schemeDefinitions(api);
        return new ContractExtractionResultDto(contractId, STATUS_EXTRACTED, true, sourceName,
                api.getOpenapi(), sha256(content), title(api), version(api), endpoints.size(),
                schemes.size(), declaredSecurity(null, api.getSecurity(), declaredSchemes(api)),
                schemes, endpoints);
    }

    private static String title(OpenAPI api) {
        return api.getInfo() == null ? null : api.getInfo().getTitle();
    }

    private static String version(OpenAPI api) {
        return api.getInfo() == null ? null : api.getInfo().getVersion();
    }

    /** Security schemes by name as declared under components/securitySchemes (empty when absent). */
    private Map<String, SecurityScheme> declaredSchemes(OpenAPI api) {
        return api.getComponents() == null || api.getComponents().getSecuritySchemes() == null
                ? Map.of()
                : api.getComponents().getSecuritySchemes();
    }

    /** The pre-existing scheme overview: scheme name to declared scheme type. */
    private Map<String, String> schemeTypes(OpenAPI api) {
        Map<String, String> types = new LinkedHashMap<>();
        declaredSchemes(api).forEach((name, scheme) -> types.put(name, typeOf(scheme)));
        return types;
    }

    /** Endpoint records for all path operations, ordered by path then method (deterministic). */
    private List<ContractEndpointDto> endpointsOf(OpenAPI api) {
        List<ContractEndpointDto> endpoints = new ArrayList<>();
        if (api.getPaths() != null) {
            api.getPaths().forEach((path, pathItem) -> collectOperations(path, pathItem, api, endpoints));
        }
        endpoints.sort(Comparator.comparing(ContractEndpointDto::path).thenComparing(ContractEndpointDto::method));
        return List.copyOf(endpoints);
    }

    private void collectOperations(String path, PathItem pathItem, OpenAPI api,
                                   List<ContractEndpointDto> endpoints) {
        if (pathItem == null) return;
        Map<String, SecurityScheme> schemes = declaredSchemes(api);
        METHOD_ORDER.forEach(httpMethod -> {
            Operation operation = operation(pathItem, httpMethod);
            if (operation == null) return;
            String method = httpMethod.name();
            List<SecurityRequirement> requirements = operation.getSecurity() != null
                    ? operation.getSecurity()
                    : api.getSecurity();
            SecurityPolicy policy = normalize(requirements, schemes);
            String jsonPath = "$.paths['" + path + "']." + method.toLowerCase(Locale.ROOT)
                    + (operation.getSecurity() != null ? ".security" : " (global security)");
            endpoints.add(new ContractEndpointDto(method, path, operation.getOperationId(), policy,
                    SOURCE_KIND_OPENAPI, jsonPath, operation.getSummary(), operation.getDescription(),
                    operation.getTags() == null ? List.of() : List.copyOf(operation.getTags()),
                    Boolean.TRUE.equals(operation.getDeprecated()),
                    declaredSecurity(operation.getSecurity(), api.getSecurity(), schemes),
                    parametersOf(operation, pathItem), requestBodyOf(operation), responsesOf(operation)));
        });
    }

    /** The operation of one HTTP method, in the fixed extraction order (all OpenAPI 3 methods). */
    private static Operation operation(PathItem pathItem, PathItem.HttpMethod method) {
        return switch (method) {
            case GET -> pathItem.getGet();
            case POST -> pathItem.getPost();
            case PUT -> pathItem.getPut();
            case PATCH -> pathItem.getPatch();
            case DELETE -> pathItem.getDelete();
            case HEAD -> pathItem.getHead();
            case OPTIONS -> pathItem.getOptions();
            case TRACE -> pathItem.getTrace();
        };
    }

    /**
     * Declared scheme definitions, ordered by scheme name so the extraction output is deterministic.
     */
    private List<ContractSecuritySchemeDto> schemeDefinitions(OpenAPI api) {
        return declaredSchemes(api).entrySet().stream()
                .map(entry -> schemeDefinition(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ContractSecuritySchemeDto::name))
                .toList();
    }

    private ContractSecuritySchemeDto schemeDefinition(String name, SecurityScheme scheme) {
        List<String> flows = new ArrayList<>();
        List<String> scopes = new ArrayList<>();
        if (scheme != null && scheme.getFlows() != null) {
            addFlow(flows, scopes, "implicit", scheme.getFlows().getImplicit());
            addFlow(flows, scopes, "password", scheme.getFlows().getPassword());
            addFlow(flows, scopes, "clientCredentials", scheme.getFlows().getClientCredentials());
            addFlow(flows, scopes, "authorizationCode", scheme.getFlows().getAuthorizationCode());
        }
        return new ContractSecuritySchemeDto(name, typeOf(scheme),
                scheme == null ? null : scheme.getScheme(),
                scheme == null ? null : scheme.getBearerFormat(),
                scheme == null || scheme.getIn() == null ? null : scheme.getIn().toString(),
                scheme == null ? null : scheme.getName(),
                scheme == null ? null : scheme.getOpenIdConnectUrl(),
                List.copyOf(flows), sortedUnique(scopes));
    }

    private void addFlow(List<String> flows, List<String> scopes, String flowName, OAuthFlow flow) {
        if (flow == null) return;
        flows.add(flowName);
        if (flow.getScopes() != null) scopes.addAll(flow.getScopes().keySet());
    }

    /**
     * Extracts the security declared for one scope, preserving OpenAPI semantics: {@code security} is
     * an OR-list of requirement objects and each requirement object is an AND-set of schemes. An
     * operation-level declaration replaces the global one entirely when present - including
     * {@code security: []}, which explicitly makes the operation public. When the operation declares
     * nothing at all, the global declaration is inherited (never reported as public).
     */
    private ContractSecurityDto declaredSecurity(List<SecurityRequirement> operationSecurity,
                                                List<SecurityRequirement> globalSecurity,
                                                Map<String, SecurityScheme> schemes) {
        List<SecurityRequirement> declared = operationSecurity != null ? operationSecurity : globalSecurity;
        String source = operationSecurity != null ? SOURCE_OPERATION
                : (globalSecurity != null ? SOURCE_GLOBAL : SOURCE_NONE);
        List<ContractSecurityAlternativeDto> alternatives = new ArrayList<>();
        if (declared != null) {
            for (SecurityRequirement requirement : declared) {
                if (requirement == null || requirement.isEmpty()) {
                    // an empty requirement object needs no credentials: anonymous alternative
                    alternatives.add(new ContractSecurityAlternativeDto(true, List.of()));
                    continue;
                }
                List<ContractSecuritySchemeRefDto> refs = new ArrayList<>();
                requirement.forEach((name, scopes) -> refs.add(schemeRef(name, scopes, schemes)));
                refs.sort(Comparator.comparing(ContractSecuritySchemeRefDto::name));
                alternatives.add(new ContractSecurityAlternativeDto(false, List.copyOf(refs)));
            }
        }
        boolean isDeclared = declared != null;
        boolean explicitlyPublic = isDeclared && declared.isEmpty();
        boolean anonymousAllowed = !isDeclared || explicitlyPublic
                || alternatives.stream().anyMatch(ContractSecurityAlternativeDto::anonymous);
        return new ContractSecurityDto(source, isDeclared, explicitlyPublic, anonymousAllowed,
                isDeclared && !anonymousAllowed, List.copyOf(alternatives));
    }

    private ContractSecuritySchemeRefDto schemeRef(String name, List<String> scopes,
                                                   Map<String, SecurityScheme> schemes) {
        SecurityScheme scheme = schemes.get(name);
        return new ContractSecuritySchemeRefDto(name, typeOf(scheme),
                scheme == null ? null : scheme.getScheme(),
                scheme == null ? null : scheme.getBearerFormat(),
                scheme == null || scheme.getIn() == null ? null : scheme.getIn().toString(),
                scheme == null ? null : scheme.getName(),
                scheme == null ? null : scheme.getOpenIdConnectUrl(),
                sortedUnique(scopes == null ? List.of() : scopes), scheme != null);
    }

    private static String typeOf(SecurityScheme scheme) {
        return scheme == null || scheme.getType() == null ? null : scheme.getType().toString();
    }

    private static List<String> sortedUnique(Collection<String> values) {
        return values.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    /** Path-level and operation-level parameters merged (operation wins), in stable order. */
    private List<ContractParameterDto> parametersOf(Operation operation, PathItem pathItem) {
        Map<String, Parameter> merged = new LinkedHashMap<>();
        addParameters(merged, pathItem.getParameters());
        addParameters(merged, operation.getParameters());
        List<ContractParameterDto> parameters = new ArrayList<>();
        merged.values().forEach(definition -> parameters.add(parameter(definition)));
        parameters.sort(Comparator.comparingInt((ContractParameterDto p) -> inRank(p.in()))
                .thenComparing(ContractParameterDto::name));
        return List.copyOf(parameters);
    }

    /** Key = location + name, so an operation parameter replaces the path-level one (OpenAPI rule). */
    private void addParameters(Map<String, Parameter> merged, List<Parameter> parameters) {
        if (parameters == null) return;
        for (Parameter definition : parameters) {
            if (definition == null || definition.getName() == null) continue;
            String in = definition.getIn() == null ? "" : definition.getIn().toLowerCase(Locale.ROOT);
            merged.put(in + ":" + definition.getName(), definition);
        }
    }

    private ContractParameterDto parameter(Parameter definition) {
        Schema<?> schema = definition.getSchema();
        return new ContractParameterDto(definition.getName(), definition.getIn(),
                Boolean.TRUE.equals(definition.getRequired()), definition.getDescription(),
                schema == null ? null : schema.getType(), schema == null ? null : schema.get$ref(),
                schema == null || schema.getItems() == null ? null : schema.getItems().getType(),
                enumValues(schema));
    }

    private static List<String> enumValues(Schema<?> schema) {
        if (schema == null || schema.getEnum() == null || schema.getEnum().isEmpty()) return List.of();
        List<String> values = new ArrayList<>();
        schema.getEnum().forEach(value -> values.add(String.valueOf(value)));
        return List.copyOf(values);
    }

    private ContractRequestBodyDto requestBodyOf(Operation operation) {
        RequestBody body = operation.getRequestBody();
        return body == null ? null : new ContractRequestBodyDto(Boolean.TRUE.equals(body.getRequired()),
                mediaTypes(body.getContent()));
    }

    /** Media types of a request body or response, ordered by media type name (deterministic). */
    private List<ContractMediaTypeDto> mediaTypes(Content content) {
        if (content == null || content.isEmpty()) return List.of();
        List<ContractMediaTypeDto> result = new ArrayList<>();
        content.forEach((mediaType, definition) -> {
            Schema<?> schema = definition == null ? null : definition.getSchema();
            result.add(new ContractMediaTypeDto(mediaType, schema == null ? null : schema.getType(),
                    schema == null ? null : schema.get$ref()));
        });
        result.sort(Comparator.comparing(ContractMediaTypeDto::mediaType));
        return List.copyOf(result);
    }

    /** Responses ordered by status code: numeric ascending, then XX ranges, then 'default'. */
    private List<ContractResponseDto> responsesOf(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null || responses.isEmpty()) return List.of();
        List<ContractResponseDto> result = new ArrayList<>();
        responses.forEach((statusCode, response) -> result.add(new ContractResponseDto(statusCode,
                response == null ? null : response.getDescription(),
                mediaTypes(response == null ? null : response.getContent()))));
        result.sort(Comparator.comparingInt((ContractResponseDto r) -> statusRank(r.statusCode()))
                .thenComparingInt(r -> statusValue(r.statusCode()))
                .thenComparing(ContractResponseDto::statusCode));
        return List.copyOf(result);
    }

    private static int statusRank(String statusCode) {
        if (statusCode == null) return 3;
        if (statusCode.matches("\\d{3}")) return 0;
        return statusCode.matches("[1-5]XX") ? 1 : 2;
    }

    private static int statusValue(String statusCode) {
        try {
            return Integer.parseInt(statusCode);
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    /** Parameter location order used for deterministic output (unknown locations last). */
    private static int inRank(String in) {
        int index = PARAMETER_IN_ORDER.indexOf(in == null ? "" : in.toLowerCase(Locale.ROOT));
        return index < 0 ? PARAMETER_IN_ORDER.size() : index;
    }

    /** Normalize OpenAPI security requirements into the shared SecurityPolicy model. */
    private SecurityPolicy normalize(List<SecurityRequirement> requirements,
                                     Map<String, SecurityScheme> schemes) {
        if (requirements == null) {
            // no global and no operation-level security: open endpoint
            return SecurityPolicy.permitAllPolicy();
        }
        if (requirements.isEmpty()) {
            // explicit empty security array means the operation is public
            return SecurityPolicy.permitAllPolicy();
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
                        for (OAuthFlow flow : new OAuthFlow[]{
                                scheme.getFlows().getImplicit(),
                                scheme.getFlows().getPassword(),
                                scheme.getFlows().getClientCredentials(),
                                scheme.getFlows().getAuthorizationCode()}) {
                            if (flow != null && flow.getScopes() != null) scopes.addAll(flow.getScopes().keySet());
                        }
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
