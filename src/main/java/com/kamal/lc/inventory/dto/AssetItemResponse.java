package com.kamal.lc.inventory.dto;

import lombok.*;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetItemResponse {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private String categoryLabelAr;
    private String serialCode;
    private String status;
    private String notes;

    /** Dynamic fields as key-value pairs, e.g. {"sim_type": "STC", "phone_number": "0501234567"} */
    private Map<String, String> fields;

    /** Current assignment info, if assigned. */
    private String currentCaptainName;
    private String currentCaptainCode;
    private UUID currentCaptainId;
    private String assignedSince;
}
