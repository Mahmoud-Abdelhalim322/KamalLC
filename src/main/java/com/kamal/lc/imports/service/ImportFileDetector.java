package com.kamal.lc.imports.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.imports.entity.ImportBatchType;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Optional;

/**
 * Works out which importer an uploaded file belongs to, by scoring its header row
 * against both parsers' column maps.
 *
 * <p>It exists for one reason: the two files share nothing but a file extension, and
 * a supplier shift report dropped on the captains uploader used to fail with
 * "لم يتم العثور على صف العناوين" — technically true, and useless to the person
 * holding the file. Detection turns that into a message naming the right screen.
 *
 * <p>Advisory only. A file it cannot classify is passed through to the parser, which
 * is still the authority on whether the file can actually be read.
 */
@Slf4j
@Service
public class ImportFileDetector {

    /**
     * Below this many recognised headers a sheet is not confidently either kind —
     * a captains file, for instance, contributes one match to the shift map because
     * both know the "ID - NINJA" column.
     */
    private static final int MIN_MATCHES = 3;

    public Optional<ImportBatchType> detect(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) return Optional.empty();

            int captains = ExcelCellSupport.detectHeaders(sheet, ExcelParserService.columnMap()).size();
            int shifts = ExcelCellSupport.detectHeaders(sheet, NinjaShiftParser.columnMap()).size();

            if (shifts >= MIN_MATCHES && shifts > captains) return Optional.of(ImportBatchType.SHIFTS);
            if (captains >= MIN_MATCHES && captains > shifts) return Optional.of(ImportBatchType.CAPTAINS);
            return Optional.empty();

        } catch (Exception e) {
            log.debug("Could not classify '{}': {}", file.getOriginalFilename(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Rejects a file that plainly belongs to the other importer, naming the screen the
     * user should have used. Silent when the file is the expected kind or unclassifiable.
     */
    public void requireNot(MultipartFile file, ImportBatchType wrongKind) {
        detect(file)
                .filter(kind -> kind == wrongKind)
                .ifPresent(kind -> {
                    throw new BusinessException("WRONG_IMPORT_TYPE", messageFor(kind));
                });
    }

    private String messageFor(ImportBatchType detected) {
        return detected == ImportBatchType.SHIFTS
                ? "هذا الملف تقرير شيفتات من المورد وليس ملف بيانات كباتن — "
                        + "استورده من تبويب «استيراد تقارير الموردين»"
                : "هذا الملف يحتوي بيانات كباتن ومعرفات وليس تقرير شيفتات — "
                        + "استورده من تبويب «استيراد بيانات الكباتن والموردين»";
    }
}
