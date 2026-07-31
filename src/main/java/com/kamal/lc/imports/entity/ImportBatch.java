package com.kamal.lc.imports.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "file_name", length = 500)
    private String fileName;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    /** Which importer produced this batch — the history screens filter on it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", nullable = false, length = 20)
    @Builder.Default
    private ImportBatchType batchType = ImportBatchType.CAPTAINS;

    @Column(name = "uploaded_at", nullable = false)
    @Builder.Default
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

    @Column(name = "row_count", nullable = false)
    @Builder.Default
    private int rowCount = 0;

    @Column(name = "imported_count", nullable = false)
    @Builder.Default
    private int importedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    @Builder.Default
    private int skippedCount = 0;

    @Column(name = "warning_count", nullable = false)
    @Builder.Default
    private int warningCount = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private int errorCount = 0;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PREVIEW";

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;
}
