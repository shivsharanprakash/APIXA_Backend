package com.apixa.conformance.investigation.service;

import com.apixa.common.error.ApiException;
import com.apixa.conformance.investigation.engine.InvestigationEngine;
import com.apixa.conformance.investigation.model.*;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InvestigationService {

    private final InvestigationEngine engine;
    private final Map<String, InvestigationSessionDto> sessions = new ConcurrentHashMap<>();

    public InvestigationService(InvestigationEngine engine) {
        this.engine = engine;
    }

    public InvestigationSessionDto start(StartInvestigationRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Investigation request must not be null");
        }
        InvestigationSessionDto session = engine.startInvestigation(request);
        sessions.put(session.sessionId(), session);

        // If auto-execute test requested, run approval immediately
        if (request.autoExecuteTest() && session.proposedTest() != null) {
            session = engine.executeApprovedTest(session, new ApproveTestRequest(true, request.targetBaseUrl(), null));
            sessions.put(session.sessionId(), session);
        }

        return session;
    }

    public InvestigationSessionDto get(String sessionId) {
        InvestigationSessionDto session = sessions.get(sessionId);
        if (session == null) {
            throw ApiException.notFound("Investigation session not found: " + sessionId);
        }
        return session;
    }

    public InvestigationSessionDto approveTest(String sessionId, ApproveTestRequest approval) {
        InvestigationSessionDto session = get(sessionId);
        if (approval == null) {
            approval = new ApproveTestRequest(true, null, null);
        }
        InvestigationSessionDto updated = engine.executeApprovedTest(session, approval);
        sessions.put(sessionId, updated);
        return updated;
    }
}
