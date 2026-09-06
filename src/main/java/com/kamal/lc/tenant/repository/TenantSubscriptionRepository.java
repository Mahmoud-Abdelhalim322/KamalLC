package com.kamal.lc.tenant.repository;

import com.kamal.lc.tenant.entity.TenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {
    List<TenantSubscription> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<TenantSubscription> findFirstByTenantIdAndStatusOrderByEndDateDesc(UUID tenantId, String status);
    List<TenantSubscription> findByStatus(String status);
    long countByPlanIdAndStatus(UUID planId, String status);
    long countByPlanId(UUID planId);
}
