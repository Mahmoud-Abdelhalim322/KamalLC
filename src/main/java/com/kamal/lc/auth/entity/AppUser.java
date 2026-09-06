package com.kamal.lc.auth.entity;

import com.kamal.lc.common.security.TenantAware;
import com.kamal.lc.common.security.TenantEntityListener;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Application user entity.
 * <ul>
 *   <li>{@code PLATFORM_ADMIN} — SaaS platform super admin, no tenant, manages all companies</li>
 *   <li>{@code ADMIN} — Company admin (was SUPER_ADMIN), has all tenant-level permissions</li>
 *   <li>{@code MANAGER} — Gets permissions from their linked {@link JobTitle}</li>
 * </ul>
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class AppUser implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_title_id")
    private JobTitle jobTitle;

    /** The tenant this user belongs to. NULL for PLATFORM_ADMIN. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "data_scope", length = 30)
    private String dataScope = "ALL_CAPTAINS";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Platform-level super admin — not tied to any tenant. */
    public boolean isPlatformAdmin() {
        return "PLATFORM_ADMIN".equals(role);
    }

    /** Company-level admin — has all permissions within their tenant. (Was SUPER_ADMIN) */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    /**
     * @deprecated Use {@link #isAdmin()} instead. Kept for backward compatibility
     * during migration but now checks for ADMIN role.
     */
    @Deprecated
    public boolean isSuperAdmin() {
        return isAdmin();
    }
}
