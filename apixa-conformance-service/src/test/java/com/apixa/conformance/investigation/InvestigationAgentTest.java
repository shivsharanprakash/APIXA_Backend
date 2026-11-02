package com.apixa.conformance.investigation;

import com.apixa.common.model.SecurityPolicy;
import com.apixa.conformance.investigation.engine.InvestigationEngine;
import com.apixa.conformance.investigation.model.*;
import com.apixa.conformance.investigation.service.InvestigationService;
import com.apixa.conformance.investigation.tools.InvestigationToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvestigationAgentTest {

    private InvestigationService service;

    @BeforeEach
    void setUp() {
        InvestigationToolRegistry tools = new InvestigationToolRegistry();
        InvestigationEngine engine = new InvestigationEngine(tools);
        service = new InvestigationService(engine);
    }

    @Test
    void testInvestigationFlowForRoleMismatch() {
        // Given: An OpenAPI contract requiring ADMIN, but implementation requiring USER
        SecurityPolicy contractPolicy = SecurityPolicy.roles("BEARER", List.of("ADMIN"));
        SecurityPolicy implPolicy = SecurityPolicy.roles("BEARER", List.of("USER"));

        StartInvestigationRequest request = new StartInvestigationRequest(
                "proj-1",
                "finding-admin-users",
                "GET",
                "/admin/users",
                "contract-1",
                "analysis-1",
                "MISMATCH",
                contractPolicy,
                implPolicy,
                "com.example.users.UserController",
                "listAdminUsers",
                "src/main/java/com/example/users/UserController.java",
                42,
                "Contract requires ADMIN authorization, while the implementation requires USER.",
                "http://localhost:8080",
                false
        );

        // When: starting investigation
        InvestigationSessionDto session = service.start(request);

        // Then: session created, steps populated, diagnosis synthesized
        assertNotNull(session);
        assertEquals("AWAITING_APPROVAL", session.status());
        assertEquals("GET", session.endpointMethod());
        assertEquals("/admin/users", session.endpointPath());
        assertTrue(session.steps().size() >= 7);

        // Verify diagnosis
        assertNotNull(session.diagnosis());
        assertEquals("MISMATCH", session.diagnosis().verdict());
        assertEquals("HIGH", session.diagnosis().severity());
        assertTrue(session.diagnosis().rootCauseAnalysis().contains("ADMIN"));
        assertTrue(session.diagnosis().rootCauseAnalysis().contains("USER"));

        // Verify targeted runtime test proposal
        ProposedTestDto test = session.proposedTest();
        assertNotNull(test);
        assertEquals("USER", test.testPrincipalRole());
        assertEquals(403, test.expectedStatusCode());
        assertTrue(test.requiresApproval());
        assertFalse(test.approved());

        // Verify proposed remediation
        RemediationDto remediation = session.remediation();
        assertNotNull(remediation);
        assertTrue(remediation.patchDiff().contains("-    @PreAuthorize(\"hasRole('USER')\")"));
        assertTrue(remediation.patchDiff().contains("+    @PreAuthorize(\"hasRole('ADMIN')\")"));
        assertTrue(remediation.regressionTestCode().contains("lowerPrivilegedUserShouldBeForbidden"));
        assertEquals("MATCH", remediation.simulatedVerdictAfterFix());

        // When: User approves the targeted verification test
        ApproveTestRequest approval = new ApproveTestRequest(true, "http://localhost:8080", null);
        InvestigationSessionDto completedSession = service.approveTest(session.sessionId(), approval);

        // Then: Status transitions to COMPLETED, test executed
        assertEquals("COMPLETED", completedSession.status());
        assertTrue(completedSession.proposedTest().approved());
        assertEquals("EXECUTED", completedSession.proposedTest().executionStatus());
        assertNotNull(completedSession.proposedTest().observedStatusCode());
    }

    @Test
    void testQuickInvestigateCompletesSynchronously() {
        SecurityPolicy contractPolicy = SecurityPolicy.roles("BEARER", List.of("ADMIN"));
        SecurityPolicy implPolicy = SecurityPolicy.roles("BEARER", List.of("USER"));

        StartInvestigationRequest request = new StartInvestigationRequest(
                "proj-1",
                "finding-01",
                "GET",
                "/admin/dashboard",
                "c-1",
                "a-1",
                "MISMATCH",
                contractPolicy,
                implPolicy,
                "com.example.AdminController",
                "getDashboard",
                "src/main/java/com/example/AdminController.java",
                20,
                "Role mismatch",
                "http://localhost:8080",
                true // autoExecuteTest
        );

        InvestigationSessionDto session = service.start(request);
        assertEquals("COMPLETED", session.status());
        assertTrue(session.proposedTest().approved());
    }
}
