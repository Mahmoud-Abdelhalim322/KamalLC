package com.kamal.lc.fleet.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleAssignmentRequest {

    @NotNull(message = "الكابتن مطلوب")
    private UUID captainId;

    @NotNull(message = "تاريخ الإرجاع المتوقع مطلوب")
    private OffsetDateTime expectedReturnAt;

    /** Optional: captain who should receive the vehicle after return. */
    private UUID nextCaptainId;

    private String notes;
}
