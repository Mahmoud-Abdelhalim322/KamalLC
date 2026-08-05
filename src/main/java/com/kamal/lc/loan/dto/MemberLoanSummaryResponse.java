package com.kamal.lc.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One row in the loans member-list: a captain or staff member with a summary
 * of their active loans.
 */
@Data
@Builder
public class MemberLoanSummaryResponse {
    private String memberId;
    private String memberCode;
    private String memberName;
    private String memberNameEn;
    private String memberType;
    private String memberTypeLabel;
    private String phone;
    private long activeLoanCount;
    private BigDecimal totalRemaining;
    private BigDecimal totalOriginal;
}
