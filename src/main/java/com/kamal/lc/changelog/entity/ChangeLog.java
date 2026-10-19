package com.kamal.lc.changelog.entity;

import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tenant-level change log entry. Tracks every significant data mutation
 * made by company administrators across: shifts, supplier IDs, fleet,
 * inventory, and captains.
 *
 * <p>Each row records a single field-level change. Bulk updates produce
 * one row per changed field so the before/after diff is fully traceable.
 */
@Entity
@Table(name = "changelog")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Logical entity category: SHIFT, SUPPLIER_ID, VEHICLE, ASSET_ITEM, CAPTAIN */
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    /** Human-readable label for the entity (e.g. captain name, plate number) */
    @Column(name = "entity_label", length = 255)
    private String entityLabel;

    /** Action verb: UPDATE, EXCLUDE, RESTORE, LINK, UNLINK, CREATE, DELETE,
     *  EXCLUDE_DEDUCTION, RESTORE_DEDUCTION */
    @Column(nullable = false, length = 30)
    private String action;

    /** Technical field name — null for entity-level actions (LINK, CREATE, etc.) */
    @Column(name = "field_name", length = 100)
    private String fieldName;

    /** Arabic display label for the field */
    @Column(name = "field_label", length = 100)
    private String fieldLabel;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "change_reason", columnDefinition = "TEXT")
    private String changeReason;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @PrePersist
    void prePersist() {
        if (changedAt == null) {
            changedAt = OffsetDateTime.now();
        }
    }
}
