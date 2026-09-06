package com.kamal.lc.custody.entity;

import com.kamal.lc.common.security.TenantAware;
import com.kamal.lc.common.security.TenantEntityListener;
import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Unified custody ledger — one row per asset assigned to a captain.
 *
 * <p>The {@code assetType} + {@code referenceId} pair points to the underlying record:
 * for VEHICLE it is the {@code vehicle_assignment.id}, for inventory items it is
 * the {@code asset_item.id}. The human-readable {@code assetLabel} is denormalised
 * for display without extra joins.
 */
@Entity
@Table(name = "custody_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class CustodyRecord implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "captain_id", nullable = false)
    private Captain captain;

    @Column(name = "asset_type", nullable = false, length = 30)
    private String assetType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "asset_label", nullable = false, length = 255)
    private String assetLabel;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "assigned_by", length = 100)
    private String assignedBy;

    @Column(name = "returned_by", length = 100)
    private String returnedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
