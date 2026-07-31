package com.kamal.lc.payroll.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Staff payroll line response — simpler structure than captain lines.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffPayrollLineResponse {
    private UUID id;
    private UUID staffId;
    private String staffCode;
    private String staffName;
    private String staffType;
    private String jobTitle;

    private BigDecimal basicSalary;
    private BigDecimal housingAllowance;
    private BigDecimal otherAllowances;
    private BigDecimal adjustmentsTotal;
    private BigDecimal gross;
    private BigDecimal net;

    private String notes;
    private java.util.List<StaffAdjustmentResponse> adjustments;
}
