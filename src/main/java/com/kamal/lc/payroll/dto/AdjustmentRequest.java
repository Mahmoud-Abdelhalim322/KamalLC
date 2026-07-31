package com.kamal.lc.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class AdjustmentRequest {

    @NotNull(message = "الكابتن مطلوب")
    private UUID captainId;

    /** One of the named types in {@code CaptainAdjustment.BONUS_TYPES / DEDUCTION_TYPES}. */
    @NotBlank(message = "نوع البند مطلوب")
    private String adjustmentType;

    /** Always positive; the type decides whether it adds or subtracts. */
    @NotNull(message = "المبلغ مطلوب")
    @Positive(message = "المبلغ يجب أن يكون أكبر من صفر")
    private BigDecimal amount;

    private String labelAr;
    private String reason;
}
