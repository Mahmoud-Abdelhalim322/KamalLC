package com.kamal.lc.common.security;

import java.util.UUID;

/**
 * ThreadLocal holder for the current tenant ID.
 * Set by {@link TenantInterceptor} after JWT authentication,
 * cleared after the request completes.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static UUID getCurrentTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Returns the current tenant ID or throws if none is set.
     * Use in services that require a tenant context.
     */
    public static UUID requireCurrentTenantId() {
        UUID id = CURRENT_TENANT.get();
        if (id == null) {
            throw new IllegalStateException("No tenant context set — this operation requires a tenant");
        }
        return id;
    }
}
