package com.kamal.lc.common.security;

import com.kamal.lc.auth.entity.AppUser;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * JPA entity listener that automatically populates the {@code tenant} property
 * of any {@link TenantAware} entity before persisting into the database.
 */
@Slf4j
public class TenantEntityListener {

    public static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @PrePersist
    @PreUpdate
    public void setTenant(Object entity) {
        if (entity instanceof TenantAware tenantAware) {
            // Platform admin users do not belong to a specific tenant
            if (entity instanceof AppUser appUser && appUser.isPlatformAdmin()) {
                return;
            }

            if (tenantAware.getTenant() == null) {
                UUID tenantId = TenantContext.getCurrentTenantId();
                if (tenantId == null) {
                    tenantId = DEFAULT_TENANT_ID;
                }
                Tenant tenant = new Tenant();
                tenant.setId(tenantId);
                tenantAware.setTenant(tenant);
            }
        }
    }
}
