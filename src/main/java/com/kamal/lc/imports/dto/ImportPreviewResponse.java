package com.kamal.lc.imports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewResponse {
    private UUID batchId;
    private String fileName;
    private int totalRows;
    private int newCaptains;
    private int existingCaptains;
    private int newNinjaIds;
    private int existingNinjaIds;
    private List<ImportIssue> issues;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportIssue {
        private int rowNumber;
        private String severity;
        private String issueCode;
        private String message;
    }
}
