package com.apixa.project.repository;

import com.apixa.project.entity.AnalysisRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRunEntity, Long> {
    List<AnalysisRunEntity> findByProjectIdOrderByIdDesc(Long projectId);
}
