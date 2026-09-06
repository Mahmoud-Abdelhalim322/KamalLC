package com.kamal.lc.tenant.repository;

import com.kamal.lc.tenant.entity.TenantInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TenantInvoiceRepository extends JpaRepository<TenantInvoice, UUID> {
    List<TenantInvoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<TenantInvoice> findByStatus(String status);
    long countByTenantId(UUID tenantId);
}
