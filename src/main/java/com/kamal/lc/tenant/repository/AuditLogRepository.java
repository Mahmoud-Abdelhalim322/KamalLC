package com.kamal.lc.tenant.repository;

import com.kamal.lc.tenant.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    Page<AuditLog> findByActorUsernameOrderByCreatedAtDesc(String actorUsername, Pageable pageable);

    /**
     * Newest first, narrowed by optional filters. Empty strings mean "no filter";
     * {@code search} must already be lower-cased and wrapped in % wildcards.
     */
    @Query(value = """
            SELECT * FROM audit_log a
            WHERE (:action = '' OR a.action = :action)
              AND (:tenantId = '' OR CAST(a.tenant_id AS text) = :tenantId)
              AND (:search = '' OR LOWER(a.actor_username) LIKE :search OR LOWER(CAST(a.details AS text)) LIKE :search)
            ORDER BY a.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM audit_log a
            WHERE (:action = '' OR a.action = :action)
              AND (:tenantId = '' OR CAST(a.tenant_id AS text) = :tenantId)
              AND (:search = '' OR LOWER(a.actor_username) LIKE :search OR LOWER(CAST(a.details AS text)) LIKE :search)
            """,
            nativeQuery = true)
    Page<AuditLog> search(@Param("action") String action, @Param("tenantId") String tenantId,
                          @Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();
}
