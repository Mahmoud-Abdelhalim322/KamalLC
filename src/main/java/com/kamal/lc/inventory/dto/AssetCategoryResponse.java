package com.kamal.lc.inventory.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetCategoryResponse {
    private UUID id;
    private String name;
    private String labelAr;
    private boolean systemDefined;
    private long itemCount;
    private List<FieldTemplateResponse> fieldTemplates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldTemplateResponse {
        private UUID id;
        private String fieldKey;
        private String labelAr;
        private int fieldOrder;
        private boolean required;
    }
}
