package com.apixa.evidence.model;

import com.apixa.evidence.entity.EvidenceEntity;

import java.util.List;

/** Step 12 response: the persisted evidence set with its linkage. */
public record EvidenceSetResponse(
        String evidenceSetId,
        Long analysisRunId,
        String contractId,
        int itemCount,
        List<EvidenceEntity> items) {
}