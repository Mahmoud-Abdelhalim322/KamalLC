package com.kamal.lc.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class LoanResponse {
    private String id;
    private String memberId;
    private String memberCode;
    private String memberName;
    private String memberType;
    private BigDecimal originalAmount;
    private BigDecimal remaining;
    private BigDecimal monthlyDeduction;
    private String status;
    private String notes;
    private String createdBy;
    private String createdAt;
    private List<LoanPaymentResponse> payments;
}
