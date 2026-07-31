package com.kamal.lc.payroll.entity;

import com.kamal.lc.captain.entity.Captain;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One captain's line in a payroll run. Captain name/code/type are copied in rather than
 * joined, so an approved run still reads the way it was approved.
 */
@Entity
@Table(name = "payroll_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "captain_id", nullable = false)
    private Captain captain;

    @Column(name = "captain_code", nullable = false, length = 50)
    private String captainCode;

    @Column(name = "captain_name", nullable = false, length = 255)
    private String captainName;

    @Column(name = "captain_type", nullable = false, length = 20)
    private String captainType;

    @Column(name = "supplier_external_ids", length = 255)
    private String supplierExternalIds;

    // ---- Activity ----

    @Column(name = "attended_shifts", nullable = false)
    @Builder.Default
    private int attendedShifts = 0;

    @Column(name = "absent_shifts", nullable = false)
    @Builder.Default
    private int absentShifts = 0;

    @Column(name = "total_orders", nullable = false)
    @Builder.Default
    private int totalOrders = 0;

    @Column(name = "total_hours", nullable = false, precision = 12, scale = 4)
    @Builder.Default
    private BigDecimal totalHours = BigDecimal.ZERO;

    @Column(name = "avg_busy_utilization", precision = 10, scale = 6)
    private BigDecimal avgBusyUtilization;

    @Column(name = "avg_hourly_utr", precision = 10, scale = 4)
    private BigDecimal avgHourlyUtr;

    @Column(name = "avg_delivery_ontime", precision = 10, scale = 6)
    private BigDecimal avgDeliveryOntime;

    // ---- Earnings ----

    @Column(name = "base_pay", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal basePay = BigDecimal.ZERO;

    @Column(name = "late_deductions", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal lateDeductions = BigDecimal.ZERO;

    @Column(name = "daily_incentives", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal dailyIncentives = BigDecimal.ZERO;

    @Column(name = "fuel_allowances", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal fuelAllowances = BigDecimal.ZERO;

    @Column(name = "car_allowances", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal carAllowances = BigDecimal.ZERO;

    @Column(name = "target_bonus", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal targetBonus = BigDecimal.ZERO;

    @Column(name = "extra_order_pay", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal extraOrderPay = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal gross = BigDecimal.ZERO;

    @Column(name = "adjustments_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal adjustmentsTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal net = BigDecimal.ZERO;

    // ---- Profitability ----

    @Column(name = "supplier_revenue_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal supplierRevenueTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal margin = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
