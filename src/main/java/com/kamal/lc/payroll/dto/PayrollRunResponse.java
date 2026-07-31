package com.kamal.lc.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class PayrollRunResponse {
    private UUID id;
    private String periodFrom;
    private String periodTo;
    private String status;
    private String title;
    private String generatedAt;
    private String generatedBy;
    private String approvedAt;
    private String approvedBy;
    private String notes;
    private boolean locked;

    private int captainCount;
    private BigDecimal totalGross;
    private BigDecimal totalNet;
    private BigDecimal totalSupplierRevenue;
    private BigDecimal totalMargin;

    private int staffCount;
    private BigDecimal totalStaffGross;
    private BigDecimal totalStaffNet;
}
