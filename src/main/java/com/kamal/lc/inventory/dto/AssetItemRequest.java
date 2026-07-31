package com.kamal.lc.inventory.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetItemRequest {

    @NotNull(message = "الفئة مطلوبة")
    private UUID categoryId;

    /** Dynamic fields as key-value pairs. */
    private Map<String, String> fields;

    private String notes;
}
