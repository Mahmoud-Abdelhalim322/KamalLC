package com.kamal.lc.shift.entity;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.imports.entity.ImportBatch;
import com.kamal.lc.supplier.entity.Supplier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One imported shift row from a supplier's file, plus everything the system computes
 * from it.
 *
 * <p>Raw supplier values are stored verbatim so a recalculation can always be redone
 * from the original numbers. {@code captainId} may be null when the supplier's captain
 * ID is not linked yet — the row is still kept so nothing is lost.
 */
@Entity
@Table(name = "shift")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ---- Identity ----

    @Column(name = "external_shift_id", nullable = false, length = 50)
    private String externalShiftId;

    /** The supplier's own captain identifier ("captain_id" in the Ninja sheet). */
    @Column(name = "ninja_captain_id", nullable = false, length = 50)
    private String ninjaCaptainId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "captain_id")
    private Captain captain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_ref_id")
    private Supplier supplier;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    // ---- Raw supplier columns ----

    @Column(name = "supplier_id", length = 50)
    private String supplierExternalId;

    @Column(name = "supplier_name", length = 255)
    private String supplierName;

    @Column(length = 100)
    private String city;

    @Column(name = "zone_name", length = 255)
    private String zoneName;

    @Column(name = "shift_name", length = 255)
    private String shiftName;

    @Column(name = "ninja_captain_name", length = 255)
    private String ninjaCaptainName;

    @Column(name = "scheduled_start")
    private OffsetDateTime scheduledStart;

    @Column(name = "scheduled_end")
    private OffsetDateTime scheduledEnd;

    @Column(name = "actual_checkin")
    private OffsetDateTime actualCheckin;

    @Column(name = "attendance_flag", length = 50)
    private String attendanceFlag;

    @Column(name = "delay_minutes", precision = 10, scale = 4)
    private BigDecimal delayMinutes;

    @Column(name = "working_hours", precision = 10, scale = 4)
    private BigDecimal workingHours;

    @Column(name = "shift_type_hours", precision = 10, scale = 4)
    private BigDecimal shiftTypeHours;

    @Column(name = "total_delivered")
    private Integer totalDelivered;

    @Column(name = "offline_time", precision = 10, scale = 4)
    private BigDecimal offlineTime;

    @Column(name = "free_time", precision = 10, scale = 4)
    private BigDecimal freeTime;

    @Column(name = "busy_time", precision = 10, scale = 4)
    private BigDecimal busyTime;

    @Column(name = "away_from_zone", precision = 10, scale = 4)
    private BigDecimal awayFromZone;

    /** Stored as a fraction (0.7625), not a percentage — matching the supplier file. */
    @Column(name = "busy_utilization", precision = 10, scale = 6)
    private BigDecimal busyUtilization;

    @Column(name = "delivery_ontime", precision = 10, scale = 6)
    private BigDecimal deliveryOntime;

    @Column(name = "hourly_utr", precision = 10, scale = 4)
    private BigDecimal hourlyUtr;

    // ---- Computed: captain pay ----

    @Column(name = "is_attended")
    private Boolean attended;

    @Column(name = "daily_wage", precision = 12, scale = 2)
    private BigDecimal dailyWage;

    @Column(name = "late_deduction", precision = 12, scale = 2)
    private BigDecimal lateDeduction;

    @Column(name = "extra_orders")
    private Integer extraOrders;

    @Column(name = "daily_incentive", precision = 12, scale = 2)
    private BigDecimal dailyIncentive;

    @Column(name = "fuel_allowance", precision = 12, scale = 2)
    private BigDecimal fuelAllowance;

    @Column(name = "car_allowance", precision = 12, scale = 2)
    private BigDecimal carAllowance;

    @Column(name = "net_shift_pay", precision = 12, scale = 2)
    private BigDecimal netShiftPay;

    // ---- Computed: supplier revenue ----

    @Column(name = "qualifies_full_rate")
    private Boolean qualifiesFullRate;

    @Column(name = "supplier_revenue", precision = 12, scale = 2)
    private BigDecimal supplierRevenue;

    @Column(name = "supplier_extra_revenue", precision = 12, scale = 2)
    private BigDecimal supplierExtraRevenue;

    @Column(name = "shift_margin", precision = 12, scale = 2)
    private BigDecimal shiftMargin;

    /** Per-shift penalty from the supplier (Ninja), imported via a separate daily sheet. */
    @Column(name = "supplier_deduction", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal supplierDeduction = BigDecimal.ZERO;

    @Column(name = "supplier_deduction_reason", length = 500)
    private String supplierDeductionReason;

    @Column(name = "calc_note", length = 500)
    private String calcNote;

    @Column(name = "settings_version")
    private LocalDate settingsVersion;

    // ---- Manual override ----

    @Column(name = "is_excluded", nullable = false)
    @Builder.Default
    private boolean excluded = false;

    @Column(name = "exclusion_reason", length = 500)
    private String exclusionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Legacy V8 column kept for the revenue side; superseded by {@link #supplierRevenue}. */
    @Column(name = "shift_income", precision = 12, scale = 2)
    private BigDecimal shiftIncome;

    @Column(name = "net_shift_value", precision = 12, scale = 2)
    private BigDecimal netShiftValue;
}
