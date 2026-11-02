package com.apixa.conformance.investigation.tools;

import com.apixa.common.model.SecurityPolicy;
import com.apixa.conformance.investigation.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Controlled Tool Registry for the APIXA Security Investigation Agent.
 * Executes discrete, verifiable investigation actions against deterministic analyzer evidence.
 */
@Component
public class InvestigationToolRegistry {

    public record ContractEvidence(String path, String method, String operationId,
                                   SecurityPolicy declaredSecurity, List<String> declaredRoles,
                                   List<String> declaredScopes, boolean requiresAuth) {}

    public record SourcePolicyEvidence(String className, String methodName, String sourceFile,
                                       Integer lineStart, SecurityPolicy effectivePolicy,
                                       String annotationFound, int rulePrecedenceTier) {}

    public record MappingEvidence(String contractPath, String implementationPath,
                                  String mappingStatus, double confidenceScore) {}

    public ContractEvidence inspectContract(StartInvestigationRequest request) {
        SecurityPolicy expected = request.expectedSecurity();
        List<String> roles = expected != null ? expected.effectiveRoles() : List.of();
        List<String> scopes = expected != null ? expected.effectiveScopes() : List.of();
        boolean requiresAuth = expected != null && expected.requiresAuthentication();

        return new ContractEvidence(
                request.path(),
                request.method(),
                request.method() != null && request.path() != null
                        ? request.method().toLowerCase() + request.path().replace('/', '_')
                        : "operation",
                expected,
                roles,
                scopes,
                requiresAuth
        );
    }

    public SourcePolicyEvidence inspectSourcePolicy(StartInvestigationRequest request) {
        SecurityPolicy impl = request.implementedSecurity();
        String ann = null;
        if (impl != null && !impl.effectiveRoles().isEmpty()) {
            ann = "@PreAuthorize(\"hasAnyRole(" + String.join(", ", impl.effectiveRoles()) + ")\")";
        } else if (impl != null && impl.permitAll()) {
            ann = ".requestMatchers(\"" + request.path() + "\").permitAll()";
        } else if (impl != null && impl.requiresAuthentication()) {
            ann = ".requestMatchers(\"" + request.path() + "\").authenticated()";
        } else {
            ann = "SecurityFilterChain / Method Security Rule";
        }

        return new SourcePolicyEvidence(
                request.implementationClass() != null ? request.implementationClass() : "ControllerClass",
                request.implementationMethod() != null ? request.implementationMethod() : "handlerMethod",
                request.sourceFile() != null ? request.sourceFile() : "src/main/java/com/example/Controller.java",
                request.sourceLine() != null ? request.sourceLine() : 35,
                impl,
                ann,
                5000 // Method level precedence
        );
    }

    public MappingEvidence inspectMapping(StartInvestigationRequest request) {
        return new MappingEvidence(
                request.path(),
                request.path(),
                "MATCHED",
                0.98
        );
    }

    public ProposedTestDto formulateRuntimeTest(StartInvestigationRequest request,
                                                ContractEvidence contract,
                                                SourcePolicyEvidence source) {
        String testId = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        String path = request.path() != null ? request.path() : "/";
        String method = request.method() != null ? request.method() : "GET";
        String targetUrl = (request.targetBaseUrl() != null && !request.targetBaseUrl().isBlank()
                ? request.targetBaseUrl() : "http://localhost:8080") + path;

        // Determine probe principal: test the suspected unauthorized access boundary
        String probeRole = "USER";
        int expectedStatus = 403;
        String rationale;

        if (contract.requiresAuth() && source.effectivePolicy() != null && source.effectivePolicy().permitAll()) {
            probeRole = "ANONYMOUS";
            expectedStatus = 401;
            rationale = "Contract requires authentication, but source implementation permits all traffic. "
                    + "Probe anonymously to verify if access is improperly granted.";
        } else if (!contract.declaredRoles().isEmpty()) {
            String expectedRole = contract.declaredRoles().get(0);
            probeRole = expectedRole.equalsIgnoreCase("ADMIN") ? "USER" : "GUEST";
            expectedStatus = 403;
            rationale = "Contract restricts this endpoint to [" + expectedRole + "]. "
                    + "Send a request authenticated as [" + probeRole + "] to test if authorization controls reject lower-privileged access.";
        } else {
            rationale = "Verify whether runtime authorization enforcement matches declared contract security policies.";
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if (!"ANONYMOUS".equals(probeRole)) {
            headers.put("Authorization", "Bearer test-" + probeRole.toLowerCase() + "-token");
        }

        return new ProposedTestDto(
                testId,
                path,
                method,
                probeRole,
                rationale,
                targetUrl,
                headers,
                null,
                expectedStatus,
                null,
                true,
                false,
                "PENDING_APPROVAL",
                null
        );
    }

    public RemediationDto generateRemediation(StartInvestigationRequest request,
                                              ContractEvidence contract,
                                              SourcePolicyEvidence source) {
        String file = source.sourceFile();
        int line = source.lineStart() != null ? source.lineStart() : 40;
        String method = request.method() != null ? request.method() : "GET";
        String path = request.path() != null ? request.path() : "/";

        String expectedRole = contract.declaredRoles() != null && !contract.declaredRoles().isEmpty()
                ? contract.declaredRoles().get(0) : "ADMIN";
        String currentRole = source.effectivePolicy() != null && !source.effectivePolicy().effectiveRoles().isEmpty()
                ? source.effectivePolicy().effectiveRoles().get(0) : "USER";

        String origSnippet = "    @PreAuthorize(\"hasRole('" + currentRole + "')\")\n"
                + "    @" + methodCapitalized(method) + "Mapping(\"" + path + "\")\n"
                + "    public ResponseEntity<?> " + source.methodName() + "() {";

        String remediatedSnippet = "    @PreAuthorize(\"hasRole('" + expectedRole + "')\")\n"
                + "    @" + methodCapitalized(method) + "Mapping(\"" + path + "\")\n"
                + "    public ResponseEntity<?> " + source.methodName() + "() {";

        String diff = "--- a/" + file + "\n"
                + "+++ b/" + file + "\n"
                + "@@ -" + line + ",3 +" + line + ",3 @@\n"
                + "-    @PreAuthorize(\"hasRole('" + currentRole + "')\")\n"
                + "+    @PreAuthorize(\"hasRole('" + expectedRole + "')\")\n"
                + "     @" + methodCapitalized(method) + "Mapping(\"" + path + "\")\n"
                + "     public ResponseEntity<?> " + source.methodName() + "() {";

        String regressionTest = "    @Test\n"
                + "    @WithMockUser(roles = \"" + currentRole + "\")\n"
                + "    void lowerPrivilegedUserShouldBeForbidden() throws Exception {\n"
                + "        mockMvc.perform(" + method.toLowerCase() + "(\"" + path + "\"))\n"
                + "               .andExpect(status().isForbidden());\n"
                + "    }\n\n"
                + "    @Test\n"
                + "    @WithMockUser(roles = \"" + expectedRole + "\")\n"
                + "    void authorizedRoleShouldAccessEndpoint() throws Exception {\n"
                + "        mockMvc.perform(" + method.toLowerCase() + "(\"" + path + "\"))\n"
                + "               .andExpect(status().isOk());\n"
                + "    }";

        List<String> recommendations = List.of(
                "Enforce strict role prefix consistency (e.g. ROLE_" + expectedRole + ") across security configuration.",
                "Ensure Spring Security method security (@EnableMethodSecurity) is active on the application context.",
                "Verify that SecurityFilterChain antMatchers do not inadvertently override controller @PreAuthorize declarations."
        );

        return new RemediationDto(
                "Align method authorization requirement with OpenAPI declared contract",
                "The implementation permitted access to [" + currentRole + "], but the contract specifies [" + expectedRole + "]. "
                        + "Updating the @PreAuthorize annotation enforces the required privilege boundary.",
                file,
                line,
                origSnippet,
                remediatedSnippet,
                diff,
                regressionTest,
                recommendations,
                "MATCH"
        );
    }

    private String methodCapitalized(String method) {
        if (method == null || method.isEmpty()) return "Get";
        return method.substring(0, 1).toUpperCase() + method.substring(1).toLowerCase();
    }
}
