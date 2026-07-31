package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.ExcelCaptainRow;
import com.kamal.lc.imports.dto.ImportPreviewResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TEMPORARY diagnostic — runs the real captain commit against the real DB, then rolls back. */
@SpringBootTest
@Transactional
class TempCaptainCommitDiagnostic {

    private static final Path REAL_FILE = Path.of(
            "C:/Users/Mahmoud AbdelHalim/Desktop/New folder (3)/العمليات/1الكباتن.xlsx");

    @Autowired
    private ImportService importService;

    @Autowired
    private ExcelParserService parser;

    @Test
    void commitRealFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "1الكباتن.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(REAL_FILE));

        // What does the sheet actually contain?
        List<ExcelCaptainRow> rows = parser.parse(file);
        System.out.println(">>> ROWS = " + rows.size());
        Map<String, Integer> byNinja = new LinkedHashMap<>();
        Map<String, Integer> byKamal = new LinkedHashMap<>();
        for (ExcelCaptainRow r : rows) {
            byNinja.merge(String.valueOf(r.getNinjaId()), 1, Integer::sum);
            byKamal.merge(String.valueOf(r.getKamalCode()), 1, Integer::sum);
        }
        byNinja.forEach((k, v) -> { if (v > 1) System.out.println(">>> DUP NINJA ID: " + k + " x" + v); });
        byKamal.forEach((k, v) -> { if (v > 1) System.out.println(">>> DUP KAMAL CODE: " + k + " x" + v); });
        rows.stream().limit(3).forEach(r -> System.out.println(
                ">>> SAMPLE row=" + r.getRowNumber() + " kamal=" + r.getKamalCode()
                        + " ninja=" + r.getNinjaId() + " name=" + r.getCaptainName()));

        try {
            ImportPreviewResponse preview = importService.preview(file);
            System.out.println(">>> PREVIEW OK batch=" + preview.getBatchId()
                    + " rows=" + preview.getTotalRows()
                    + " newCaptains=" + preview.getNewCaptains()
                    + " issues=" + preview.getIssues().size());

            ImportPreviewResponse committed = importService.commit(file, preview.getBatchId());
            System.out.println(">>> COMMIT OK imported=" + committed.getNewCaptains()
                    + " skipped=" + committed.getExistingCaptains()
                    + " issues=" + committed.getIssues().size());
            committed.getIssues().stream().limit(10)
                    .forEach(i -> System.out.println(">>> ISSUE " + i.getSeverity() + ": " + i.getMessage()));
        } catch (Exception e) {
            System.out.println(">>> FAILED WITH: " + e.getClass().getName());
            System.out.println(">>> MESSAGE: " + e.getMessage());
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
                System.out.println(">>> CAUSED BY: " + root.getClass().getName() + " :: " + root.getMessage());
            }
            throw e;
        }
    }
}
