package com.kamal.lc.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for registering a new supplier ID. Linking it to a captain is optional —
 * an unlinked slot simply shows up as "متاح" in the registry.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierIdSlotRequest {

    @NotNull(message = "المورد مطلوب")
    private UUID supplierId;

    @NotBlank(message = "رقم المعرف مطلوب")
    private String externalId;

    private String notes;

    // Optional assignment
    private UUID captainId;
    private LocalDate validFrom;
    private String supplierSideName;
    private String reason;
}
