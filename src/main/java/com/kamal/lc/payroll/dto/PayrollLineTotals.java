package com.kamal.lc.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** Column totals over every captain line matching the payroll table filters (the table footer). */
@Data
@Builder
public class PayrollLineTotals {
    private long shifts;
    private long orders;
    private BigDecimal basePay;
    private BigDecimal deductions;
    private BigDecimal supplierDeductions;
    private BigDecimal incentives;
    private BigDecimal car;
    private BigDecimal targetBonus;
    private BigDecimal extraOrderPay;
    private BigDecimal vacationCommission;
    private BigDecimal gross;
    private BigDecimal activeLoans;
    private BigDecimal adjustments;
    private BigDecimal net;
    private BigDecimal revenue;
    private BigDecimal margin;
}
