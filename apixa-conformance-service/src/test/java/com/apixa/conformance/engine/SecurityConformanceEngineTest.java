package com.apixa.conformance.engine;

import com.apixa.common.model.SecurityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SecurityConformanceEngine Static Conformance Tests")
class SecurityConformanceEngineTest {

    private SecurityConformanceEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SecurityConformanceEngine();
    }

    @Test
    @DisplayName("Both public (permitAll) yields MATCH")
    void testBothPublicYieldsMatch() {
        SecurityPolicy expected = SecurityPolicy.permitAllPolicy();
        SecurityPolicy implemented = SecurityPolicy.permitAllPolicy();

        SecurityConformanceEngine.Finding finding = engine.compare(expected, implemented);
        assertEquals(SecurityConformanceEngine.Result.MATCH, finding.result());
    }

    @Test
    @DisplayName("Contract public, implementation protected yields MISMATCH")
    void testContractPublicImplProtectedYieldsMismatch() {
        SecurityPolicy expected = SecurityPolicy.permitAllPolicy();
        SecurityPolicy implemented = SecurityPolicy.roles("AUTHENTICATED", List.of("ADMIN"));

        SecurityConformanceEngine.Finding finding = engine.compare(expected, implemented);
        assertEquals(SecurityConformanceEngine.Result.MISMATCH, finding.result());
    }

    @Test
    @DisplayName("Contract protected, implementation permitAll yields MISMATCH")
    void testContractProtectedImplPermitAllYieldsMismatch() {
        SecurityPolicy expected = SecurityPolicy.roles("AUTHENTICATED", List.of("ADMIN"));
        SecurityPolicy implemented = SecurityPolicy.permitAllPolicy();

        SecurityConformanceEngine.Finding finding = engine.compare(expected, implemented);
        assertEquals(SecurityConformanceEngine.Result.MISMATCH, finding.result());
    }

    @Test
    @DisplayName("Exact role match (ignoring ROLE_ prefix) yields MATCH")
    void testRoleMatchIgnoringPrefixYieldsMatch() {
        SecurityPolicy expected = SecurityPolicy.roles("AUTHENTICATED", List.of("ADMIN", "DOCTOR"));
        SecurityPolicy implemented = SecurityPolicy.roles("AUTHENTICATED", List.of("ROLE_ADMIN", "ROLE_DOCTOR"));

        SecurityConformanceEngine.Finding finding = engine.compare(expected, implemented);
        assertEquals(SecurityConformanceEngine.Result.MATCH, finding.result());
    }

    @Test
    @DisplayName("Role mismatch yields MISMATCH")
    void testRoleMismatchYieldsMismatch() {
        SecurityPolicy expected = SecurityPolicy.roles("AUTHENTICATED", List.of("ADMIN"));
        SecurityPolicy implemented = SecurityPolicy.roles("AUTHENTICATED", List.of("USER"));

        SecurityConformanceEngine.Finding finding = engine.compare(expected, implemented);
        assertEquals(SecurityConformanceEngine.Result.MISMATCH, finding.result());
    }

    @Test
    @DisplayName("Contract requires roles, implementation only requires authentication yields PARTIAL")
    void testContractRoleImplAuthOnlyYieldsPartial() {
        SecurityPolicy expected = SecurityPolicy.roles("AUTHENTICATED", List.of("ADMIN"));
        SecurityPolicy implemented = SecurityPolicy.authenticated();

        SecurityConformanceEngine.Finding finding = engine.compare(expected, implemented);
        assertEquals(SecurityConformanceEngine.Result.PARTIAL, finding.result());
    }

    @Test
    @DisplayName("Implementation enforces stricter role authorization than contract authentication yields PARTIAL")
    void testImplStricterYieldsPartial() {
        SecurityPolicy expected = SecurityPolicy.authenticated();
        SecurityPolicy implemented = SecurityPolicy.roles("AUTHENTICATED", List.of("ADMIN"));

        SecurityConformanceEngine.Finding finding = engine.compare(expected, implemented);
        assertEquals(SecurityConformanceEngine.Result.PARTIAL, finding.result());
    }

    @Test
    @DisplayName("Unknown implementation yields UNVERIFIED")
    void testUnknownImplYieldsUnverified() {
        SecurityPolicy expected = SecurityPolicy.roles("AUTHENTICATED", List.of("ADMIN"));
        SecurityPolicy implemented = SecurityPolicy.UNKNOWN;

        SecurityConformanceEngine.Finding finding = engine.compare(expected, implemented);
        assertEquals(SecurityConformanceEngine.Result.UNVERIFIED, finding.result());
    }

    @Test
    @DisplayName("Unknown contract requirement yields UNVERIFIED")
    void testUnknownContractYieldsUnverified() {
        SecurityPolicy expected = SecurityPolicy.UNKNOWN;
        SecurityPolicy implemented = SecurityPolicy.roles("AUTHENTICATED", List.of("ADMIN"));

        SecurityConformanceEngine.Finding finding = engine.compare(expected, implemented);
        assertEquals(SecurityConformanceEngine.Result.UNVERIFIED, finding.result());
    }
}
