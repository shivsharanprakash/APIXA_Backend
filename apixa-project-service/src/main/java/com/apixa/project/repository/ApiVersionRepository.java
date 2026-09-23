package com.apixa.project.repository;

import com.apixa.project.entity.ApiVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApiVersionRepository extends JpaRepository<ApiVersionEntity, Long> {
    List<ApiVersionEntity> findByProjectIdOrderByIdDesc(Long projectId);
    boolean existsByProjectIdAndVersionLabel(Long projectId, String versionLabel);
}
