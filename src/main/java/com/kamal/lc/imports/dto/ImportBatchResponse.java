package com.kamal.lc.imports.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportBatchResponse {
    private UUID id;
    private String fileName;
    private String uploadedAt;
    private int rowCount;
    private int importedCount;
    private int skippedCount;
    private int warningCount;
    private int errorCount;
    private String status;

    /** CAPTAINS or SHIFTS — the history screens render different columns per kind. */
    private String batchType;

    /** Shift batches only: the span of days the file covered. */
    private String periodFrom;
    private String periodTo;
}
