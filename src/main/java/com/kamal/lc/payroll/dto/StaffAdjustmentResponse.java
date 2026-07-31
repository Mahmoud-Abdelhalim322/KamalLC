package com.kamal.lc.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class StaffAdjustmentResponse {
    private UUID id;
    private UUID staffId;
    private String adjustmentType;
    private String kind;
    private String labelAr;
    private BigDecimal amount;
    private BigDecimal signedAmount;
    private String reason;
    private String periodFrom;
    private String periodTo;
    private String createdBy;
    private String createdAt;
}
