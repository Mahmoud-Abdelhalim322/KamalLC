package com.kamal.lc.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/** Totals over every staff line matching the payroll table filters. */
@Data
@AllArgsConstructor
public class StaffPayrollLineTotals {
    private BigDecimal gross;
    private BigDecimal adjustments;
    private BigDecimal net;
}
