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
public class DeductionImportPreviewResponse {
    private UUID batchId;
    private String fileName;
    private int totalRows;
    private int matchedRows;
    private int unmatchedRows;
    private BigDecimal totalDeductionAmount;
    private List<ImportIssue> issues;
    private List<DeductionRowPreview> rows;

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
    public static class DeductionRowPreview {
        private int rowNumber;
        private String ninjaId;
        private String kslscId;
        private String captainName;
        private LocalDate shiftDate;
        private BigDecimal amount;
        private String reason;
        private boolean matched;
        private String matchingScope; // NINJA_ID, KSLSC_ID, PHONE_OR_NATIONAL_ID, UNMATCHED
        private int matchedShiftCount;
        private String statusMessage;
    }
}
