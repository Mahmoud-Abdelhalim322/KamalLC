package com.kamal.lc.payroll.entity;

import com.kamal.lc.common.security.TenantAware;
import com.kamal.lc.common.security.TenantEntityListener;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A payroll for one period. Approving it freezes its lines: later imports and rate
 * changes must not restate a month that has already been paid out.
 */
@Entity
@Table(name = "payroll_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PayrollRun implements TenantAware {

    public static final String DRAFT = "DRAFT";
    public static final String APPROVED = "APPROVED";
    public static final String PAID = "PAID";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = DRAFT;

    @Column(length = 255)
    private String title;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "generated_by", length = 100)
    private String generatedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "paid_by", length = 100)
    private String paidBy;

    /** The rates used at generation time, so an approved run can always explain itself. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_snapshot", columnDefinition = "jsonb")
    private String settingsSnapshot;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Locked runs reject regeneration, adjustment edits, and shift writes in their period. */
    public boolean isLocked() {
        return APPROVED.equals(status) || PAID.equals(status);
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

}
