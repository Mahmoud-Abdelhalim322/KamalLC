package com.kamal.lc.payroll.entity;

import com.kamal.lc.common.security.TenantAware;
import com.kamal.lc.common.security.TenantEntityListener;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.tenant.entity.Tenant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A manual line on a staff's payroll.
 */
@Entity
@Table(name = "staff_adjustment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class StaffAdjustment implements TenantAware {

    public static final String BONUS = "BONUS";
    public static final String DEDUCTION = "DEDUCTION";

    /** Adjustment types that add to net pay */
    public static final List<String> BONUS_TYPES = List.of("REWARD", "OTHER_BONUS");

    /** Adjustment types that subtract from net pay. */
    public static final List<String> DEDUCTION_TYPES =
            List.of("SUPPLIER_PENALTY", "LOAN", "OTHER_DEDUCTION");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    /** BONUS or DEDUCTION */
    @Column(nullable = false, length = 20)
    private String kind;

    @Column(name = "adjustment_type", nullable = false, length = 40)
    private String adjustmentType;

    @Column(name = "label_ar", length = 255)
    private String labelAr;

    /** Always positive; {@link #kind} decides the sign. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_run_id")
    private PayrollRun payrollRun;

    @Column(name = "created_by", length = 100)
    private String createdBy;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static String kindFor(String adjustmentType) {
        return BONUS_TYPES.contains(adjustmentType) ? BONUS : DEDUCTION;
    }

    public static String defaultLabel(String adjustmentType) {
        return switch (adjustmentType) {
            case "REWARD" -> "مكافأة";
            case "OTHER_BONUS" -> "إضافة أخرى";
            case "SUPPLIER_PENALTY" -> "غرامات المورد";
            case "LOAN" -> "سلف";
            case "OTHER_DEDUCTION" -> "خصم آخر";
            default -> adjustmentType;
        };
    }
}
