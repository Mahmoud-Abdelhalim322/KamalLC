package com.kamal.lc.payroll.entity;

import com.kamal.lc.staff.entity.Staff;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One staff member's line in a payroll run.
 * Workers and managers have a simpler salary structure: basic + housing + other allowances.
 */
@Entity
@Table(name = "staff_payroll_line")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffPayrollLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "staff_code", nullable = false, length = 50)
    private String staffCode;

    @Column(name = "staff_name", nullable = false, length = 255)
    private String staffName;

    @Column(name = "staff_type", nullable = false, length = 20)
    private String staffType;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "basic_salary", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "housing_allowance", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal housingAllowance = BigDecimal.ZERO;

    @Column(name = "other_allowances", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal otherAllowances = BigDecimal.ZERO;

    @Column(name = "adjustments_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal adjustmentsTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal gross = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal net = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
