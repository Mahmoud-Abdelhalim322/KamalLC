package com.kamal.lc.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetCategoryRequest {

    @NotBlank(message = "اسم الفئة مطلوب")
    private String labelAr;

    /** Slug name, auto-generated from labelAr if not provided. */
    private String name;

    private List<FieldTemplateRequest> fieldTemplates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldTemplateRequest {
        private String fieldKey;
        private String labelAr;
        private int fieldOrder;
        private boolean required;
    }
}
