package com.kamal.lc.changelog.repository;

import com.kamal.lc.changelog.entity.ChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, UUID>,
        JpaSpecificationExecutor<ChangeLog> {

    @Query("SELECT DISTINCT c.changedBy FROM ChangeLog c WHERE c.tenantId = :tenantId ORDER BY c.changedBy")
    List<String> findDistinctChangedByForTenant(UUID tenantId);
}
