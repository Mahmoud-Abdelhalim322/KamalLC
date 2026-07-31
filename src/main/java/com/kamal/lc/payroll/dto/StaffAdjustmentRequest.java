package com.kamal.lc.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class StaffAdjustmentRequest {
    @NotNull(message = "معرف الموظف مطلوب")
    private UUID staffId;

    @NotBlank(message = "نوع البند مطلوب")
    private String adjustmentType;

    @NotNull(message = "المبلغ مطلوب")
    @Positive(message = "المبلغ يجب أن يكون أكبر من صفر")
    private BigDecimal amount;

    private String labelAr;
    private String reason;
}
