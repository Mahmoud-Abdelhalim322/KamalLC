package com.kamal.lc.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetAssignRequest {

    @NotNull(message = "الكابتن مطلوب")
    private UUID captainId;

    private String notes;
}
