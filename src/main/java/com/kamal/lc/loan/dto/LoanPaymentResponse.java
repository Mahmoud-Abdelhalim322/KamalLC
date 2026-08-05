package com.kamal.lc.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LoanPaymentResponse {
    private String id;
    private String loanId;
    private BigDecimal amount;
    private String source;
    private String sourceLabel;
    private String payrollRunId;
    private String notes;
    private String createdBy;
    private String createdAt;
}
