package com.kamal.lc.common.security;

import com.kamal.lc.tenant.entity.Tenant;

/**
 * Interface implemented by all tenant-scoped entities.
 * Allows {@link TenantEntityListener} to inspect and automatically set the tenant.
 */
public interface TenantAware {
    Tenant getTenant();
    void setTenant(Tenant tenant);
}
