package com.kamal.lc.imports.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ShiftImportPreviewResponse {

    private UUID batchId;
    private String fileName;
    private int totalRows;

    /** Period covered by the file, derived from the earliest and latest shift date. */
    private String periodFrom;
    private String periodTo;

    private int newShifts;
    private int updatedShifts;
    private int matchedCaptains;
    private int unmatchedRows;
    private List<String> unmatchedExternalIds;
    private int absentRows;
    private int committedRows;
    private int skippedRows;

    private List<ImportPreviewResponse.ImportIssue> issues;
}
