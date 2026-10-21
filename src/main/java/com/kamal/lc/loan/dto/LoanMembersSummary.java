package com.kamal.lc.loan.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Totals over every member matching the loans list filters, for the KPI strip. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanMembersSummary {
    private long memberCount;
    private long membersWithLoans;
    private long activeLoanCount;
    private BigDecimal totalRemaining;
    private BigDecimal totalOriginal;
}
