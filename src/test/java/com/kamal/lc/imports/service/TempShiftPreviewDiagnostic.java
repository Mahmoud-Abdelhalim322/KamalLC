package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.ShiftImportPreviewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

/** TEMPORARY diagnostic — runs the real preview against the real DB, then rolls back. */
@SpringBootTest
@Transactional
class TempShiftPreviewDiagnostic {

    private static final Path REAL_FILE = Path.of(
            "C:/Users/Mahmoud AbdelHalim/Desktop/New folder (3)/العمليات/تقارير نينجا.xlsx");

    @Autowired
    private ShiftImportService shiftImportService;

    @Test
    void previewRealFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "تقارير نينجا.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(REAL_FILE));

        try {
            ShiftImportPreviewResponse result = shiftImportService.preview(file);
            System.out.println(">>> PREVIEW OK rows=" + result.getTotalRows()
                    + " new=" + result.getNewShifts()
                    + " matched=" + result.getMatchedCaptains()
                    + " unmatched=" + result.getUnmatchedRows()
                    + " issues=" + result.getIssues().size());

            ShiftImportPreviewResponse committed =
                    shiftImportService.commit(file, result.getBatchId());
            System.out.println(">>> COMMIT OK imported=" + committed.getCommittedRows()
                    + " skipped=" + committed.getSkippedRows()
                    + " issues=" + committed.getIssues().size());
            committed.getIssues().stream()
                    .filter(i -> "ERROR".equals(i.getSeverity()))
                    .limit(5)
                    .forEach(i -> System.out.println(">>> ROW ERROR: " + i.getMessage()));
        } catch (Exception e) {
            System.out.println(">>> FAILED WITH: " + e.getClass().getName());
            System.out.println(">>> MESSAGE: " + e.getMessage());
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
                System.out.println(">>> CAUSED BY: " + root.getClass().getName() + " :: " + root.getMessage());
            }
            e.printStackTrace(System.out);
            throw e;
        }
    }
}
