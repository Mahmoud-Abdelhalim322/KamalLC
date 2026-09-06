package com.kamal.lc.imports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VacationCommissionImportPreviewResponse {
    private UUID batchId;
    private String fileName;
    private int totalRows;
    private int matchedRows;
    private int unmatchedRows;
    private BigDecimal totalAmount;
    private List<ImportIssue> issues;
    private List<VacationRowPreview> rows;

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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VacationRowPreview {
        private int rowNumber;
        private String daysCode;
        private String externalCaptainId;
        private String captainCode;
        private String captainName;
        private String supplierName;
        private String zoneName;
        private String flag;
        private String note;
        private BigDecimal amount;
        private LocalDate vacationDate;
        private boolean matched;
        private String matchingScope;
        private String statusMessage;
    }
}
