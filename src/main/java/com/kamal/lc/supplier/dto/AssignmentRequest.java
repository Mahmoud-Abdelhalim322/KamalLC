package com.kamal.lc.supplier.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/** Links an existing supplier ID slot to a captain. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentRequest {

    @NotNull(message = "الكابتن مطلوب")
    private UUID captainId;

    /** Defaults to today when omitted. */
    private LocalDate validFrom;

    private String supplierSideName;
    private String reason;
}
