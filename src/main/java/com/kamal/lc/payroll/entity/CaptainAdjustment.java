package com.kamal.lc.payroll.entity;

import com.kamal.lc.captain.entity.Captain;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A manual line on a captain's payroll — the columns Kamal fills in by hand today
 * (بدل سكن، مكافأة، غرامات نينجا، سلف، فروقات البنزين).
 *
 * <p>Typed rather than free-text so the payroll export can put each one in its own
 * column and so totals per kind are reportable.
 */
@Entity
@Table(name = "captain_adjustment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaptainAdjustment {

    public static final String BONUS = "BONUS";
    public static final String DEDUCTION = "DEDUCTION";

    /** Adjustment types that add to net pay, in the order the payroll sheet shows them. */
    public static final List<String> BONUS_TYPES =
            List.of("HOUSING_ALLOWANCE", "REWARD", "FUEL_DIFFERENCE", "OTHER_BONUS");

    /** Adjustment types that subtract from net pay. */
    public static final List<String> DEDUCTION_TYPES =
            List.of("SUPPLIER_PENALTY", "LOAN", "OTHER_DEDUCTION");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "captain_id", nullable = false)
    private Captain captain;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    /** BONUS or DEDUCTION — derived from {@link #adjustmentType}, stored for querying. */
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static String kindFor(String adjustmentType) {
        return BONUS_TYPES.contains(adjustmentType) ? BONUS : DEDUCTION;
    }

    /** Arabic label used when the caller doesn't supply one, matching the payroll sheet. */
    public static String defaultLabel(String adjustmentType) {
        return switch (adjustmentType) {
            case "HOUSING_ALLOWANCE" -> "بدل سكن";
            case "REWARD" -> "مكافأة";
            case "FUEL_DIFFERENCE" -> "فروقات البنزين";
            case "OTHER_BONUS" -> "إضافة أخرى";
            case "SUPPLIER_PENALTY" -> "غرامات المورد";
            case "LOAN" -> "سلف";
            case "OTHER_DEDUCTION" -> "خصم آخر";
            default -> adjustmentType;
        };
    }
}
