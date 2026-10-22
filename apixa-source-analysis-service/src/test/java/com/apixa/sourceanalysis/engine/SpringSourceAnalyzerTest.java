package com.apixa.sourceanalysis.engine;

import com.apixa.sourceanalysis.model.SecurityEvidenceDto;
import com.apixa.sourceanalysis.model.SourceAnalysisResultDto;
import com.apixa.sourceanalysis.model.SourceEndpointDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpringSourceAnalyzer Static Analysis & Regression Tests")
class SpringSourceAnalyzerTest {

    private SpringSourceAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SpringSourceAnalyzer();
    }

    @Test
    @DisplayName("Extracts @PreAuthorize with standalone 'authenticated' without parentheses (Repaired Defect)")
    void testPreAuthorizeAuthenticatedWithoutParentheses(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("TestController.java");
        Files.writeString(src, """
            package com.example.demo;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.prepost.PreAuthorize;

            @RestController
            @RequestMapping("/api/test")
            public class TestController {

                @PreAuthorize("authenticated")
                @GetMapping("/status")
                public String getStatus() {
                    return "OK";
                }
            }
            """);

        SourceAnalysisResultDto result = analyzer.analyzeSecurity(tempDir.toString());
        assertNotNull(result);
        List<SecurityEvidenceDto> rules = result.securityRules();
        assertNotNull(rules);
        assertFalse(rules.isEmpty(), "Security rule must be extracted");

        SecurityEvidenceDto rule = rules.stream()
                .filter(r -> "getStatus".equals(r.methodName()))
                .findFirst()
                .orElse(null);

        assertNotNull(rule, "Rule for getStatus should exist");
        assertEquals("AUTHENTICATED", rule.ruleType(), "Rule type must be AUTHENTICATED");
        assertEquals("AUTHENTICATED", rule.operator(), "Operator must be AUTHENTICATED");
        assertFalse(rule.unresolved(), "Rule must NOT be marked unresolved");
        assertFalse(rule.complex(), "Rule must NOT be marked complex");
    }

    @Test
    @DisplayName("Extracts @PreAuthorize with 'isAuthenticated()' with parentheses")
    void testPreAuthorizeIsAuthenticatedWithParentheses(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("AuthController.java");
        Files.writeString(src, """
            package com.example.demo;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.prepost.PreAuthorize;

            @RestController
            public class AuthController {

                @PreAuthorize("isAuthenticated()")
                @GetMapping("/secure")
                public String secure() {
                    return "secure";
                }
            }
            """);

        SourceAnalysisResultDto result = analyzer.analyzeSecurity(tempDir.toString());
        assertNotNull(result);
        List<SecurityEvidenceDto> rules = result.securityRules();
        SecurityEvidenceDto rule = rules.stream()
                .filter(r -> "secure".equals(r.methodName()))
                .findFirst()
                .orElse(null);

        assertNotNull(rule);
        assertEquals("AUTHENTICATED", rule.ruleType());
        assertFalse(rule.unresolved());
    }

    @Test
    @DisplayName("Extracts @PreAuthorize hasRole and hasAnyRole")
    void testPreAuthorizeRoles(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("RoleController.java");
        Files.writeString(src, """
            package com.example.demo;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.prepost.PreAuthorize;

            @RestController
            @RequestMapping("/api/roles")
            public class RoleController {

                @PreAuthorize("hasRole('ADMIN')")
                @GetMapping("/admin")
                public String adminOnly() { return "admin"; }

                @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
                @GetMapping("/records")
                public String clinicalRecords() { return "records"; }
            }
            """);

        SourceAnalysisResultDto result = analyzer.analyzeSecurity(tempDir.toString());
        assertNotNull(result);
        List<SecurityEvidenceDto> rules = result.securityRules();

        SecurityEvidenceDto adminRule = rules.stream()
                .filter(r -> "adminOnly".equals(r.methodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(adminRule);
        assertEquals("ROLE", adminRule.ruleType());
        assertEquals("HAS_ROLE", adminRule.operator());
        assertTrue(adminRule.roles().contains("ADMIN"));

        SecurityEvidenceDto multiRule = rules.stream()
                .filter(r -> "clinicalRecords".equals(r.methodName()))
                .findFirst()
                .orElse(null);
        assertNotNull(multiRule);
        assertEquals("ROLE", multiRule.ruleType());
        assertEquals("HAS_ANY_ROLE", multiRule.operator());
        assertTrue(multiRule.roles().contains("DOCTOR"));
        assertTrue(multiRule.roles().contains("ADMIN"));
    }

    @Test
    @DisplayName("Flags complex custom SpEL expression as unresolved")
    void testComplexSpelExpression(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("CustomController.java");
        Files.writeString(src, """
            package com.example.demo;
            import org.springframework.web.bind.annotation.*;
            import org.springframework.security.access.prepost.PreAuthorize;

            @RestController
            public class CustomController {

                @PreAuthorize("@mySecurityService.hasAccess(authentication, #id)")
                @GetMapping("/custom/{id}")
                public String customAccess(@PathVariable String id) {
                    return id;
                }
            }
            """);

        SourceAnalysisResultDto result = analyzer.analyzeSecurity(tempDir.toString());
        assertNotNull(result);
        List<SecurityEvidenceDto> rules = result.securityRules();

        SecurityEvidenceDto customRule = rules.stream()
                .filter(r -> "customAccess".equals(r.methodName()))
                .findFirst()
                .orElse(null);

        assertNotNull(customRule);
        assertEquals("EXPRESSION", customRule.ruleType());
        assertTrue(customRule.unresolved(), "Custom bean SpEL must be flagged unresolved");
    }

    @Test
    @DisplayName("Discovers endpoints with combined class-level and method-level paths")
    void testEndpointDiscovery(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("PatientController.java");
        Files.writeString(src, """
            package com.example.demo;
            import org.springframework.web.bind.annotation.*;

            @RestController
            @RequestMapping("/api/v1/patients")
            public class PatientController {

                @GetMapping
                public String list() { return "list"; }

                @GetMapping("/{patientId}")
                public String get(@PathVariable String patientId) { return patientId; }

                @PostMapping
                public String create() { return "created"; }
            }
            """);

        SourceAnalysisResultDto result = analyzer.analyzeEndpoints(tempDir.toString());
        assertNotNull(result);
        List<SourceEndpointDto> endpoints = result.endpoints();
        assertNotNull(endpoints);
        assertEquals(3, endpoints.size(), "Should discover 3 endpoints");

        assertTrue(endpoints.stream().anyMatch(e -> "GET".equals(e.httpMethod()) && "/api/v1/patients".equals(e.path())));
        assertTrue(endpoints.stream().anyMatch(e -> "GET".equals(e.httpMethod()) && "/api/v1/patients/{patientId}".equals(e.path())));
        assertTrue(endpoints.stream().anyMatch(e -> "POST".equals(e.httpMethod()) && "/api/v1/patients".equals(e.path())));
    }
}
