package com.kamal.lc.loan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class LoanRequest {

    /** Exactly one of captainId / staffId must be set. */
    private UUID captainId;
    private UUID staffId;

    @NotNull
    private String memberType; // CAPTAIN, WORKER, MANAGER

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @DecimalMin(value = "0")
    private BigDecimal monthlyDeduction;

    private String notes;
}
