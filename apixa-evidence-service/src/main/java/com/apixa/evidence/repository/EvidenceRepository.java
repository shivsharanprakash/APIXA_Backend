package com.apixa.evidence.repository;

import com.apixa.evidence.entity.EvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<EvidenceEntity, Long> {
    Optional<EvidenceEntity> findByEvidenceCode(String evidenceCode);
    List<EvidenceEntity> findByAnalysisRunIdOrderByIdAsc(Long analysisRunId);
    List<EvidenceEntity> findBySourceType(String sourceType);

    /** Step 12: one analysis chain. Ordered by id so retrieval is deterministic. */
    List<EvidenceEntity> findByEvidenceSetCodeOrderByIdAsc(String evidenceSetCode);
}
