package com.apixa.evidence.service;

import com.apixa.common.error.ApiException;
import com.apixa.evidence.entity.EvidenceEntity;
import com.apixa.evidence.repository.EvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EvidenceService {

    private final EvidenceRepository repository;

    public EvidenceService(EvidenceRepository repository) { this.repository = repository; }

    @Transactional
    public EvidenceEntity store(EvidenceEntity evidence) {
        return repository.save(evidence);
    }

    public EvidenceEntity byCode(String code) {
        return repository.findByEvidenceCode(code)
                .orElseThrow(() -> ApiException.notFound("Evidence " + code + " not found"));
    }

    public List<EvidenceEntity> byRun(Long analysisRunId) {
        return repository.findByAnalysisRunIdOrderByIdAsc(analysisRunId);
    }

    public List<EvidenceEntity> all() { return repository.findAll(); }
}
