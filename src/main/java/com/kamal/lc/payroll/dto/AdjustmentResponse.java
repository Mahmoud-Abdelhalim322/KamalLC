package com.kamal.lc.payroll.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class AdjustmentResponse {
    private UUID id;
    private UUID captainId;
    private String adjustmentType;
    private String kind;
    private String labelAr;
    private BigDecimal amount;
    /** {@code amount} with the sign implied by {@code kind} — what the total actually adds. */
    private BigDecimal signedAmount;
    private String reason;
    private String periodFrom;
    private String periodTo;
    private String createdBy;
    private String createdAt;
}
