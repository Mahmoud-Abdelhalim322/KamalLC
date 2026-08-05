package com.kamal.lc.imports.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.imports.entity.ImportBatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards the mistake this class exists for: the supplier's shift report and Kamal's
 * captain roster are both .xlsx files with nothing else in common, and dropping one
 * on the other's uploader used to fail with an unactionable parser error.
 */
class ImportFileDetectorTest {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportFileDetector detector = new ImportFileDetector();

    @Test
    @DisplayName("recognises the supplier's shift export")
    void detectsShiftReport() {
        assertThat(detector.detect(ninjaReport())).contains(ImportBatchType.SHIFTS);
    }

    @Test
    @DisplayName("recognises the captain template, whose 'ID - NINJA' column both parsers know")
    void detectsCaptainFile() {
        assertThat(detector.detect(captainTemplate())).contains(ImportBatchType.CAPTAINS);
    }

    @Test
    @DisplayName("a shift report on the captains uploader names the screen to use")
    void rejectsShiftReportOnCaptainsImport() {
        assertThatThrownBy(() -> detector.requireNot(ninjaReport(), ImportBatchType.SHIFTS))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException ex = (BusinessException) e;
                    assertThat(ex.getErrorCode()).isEqualTo("WRONG_IMPORT_TYPE");
                    assertThat(ex.getMessageAr()).contains("تقارير الموردين");
                });
    }

    @Test
    @DisplayName("a captain file on the reports uploader names the screen to use")
    void rejectsCaptainFileOnShiftImport() {
        assertThatThrownBy(() -> detector.requireNot(captainTemplate(), ImportBatchType.CAPTAINS))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getMessageAr())
                        .contains("الكباتن والموردين"));
    }

    @Test
    @DisplayName("each file passes the guard on its own screen")
    void allowsTheRightFileOnEachScreen() {
        assertThatCode(() -> detector.requireNot(ninjaReport(), ImportBatchType.CAPTAINS))
                .doesNotThrowAnyException();
        assertThatCode(() -> detector.requireNot(captainTemplate(), ImportBatchType.SHIFTS))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the shift template is classified as a report, not a captain file")
    void detectsShiftTemplate() {
        MockMultipartFile template = new MockMultipartFile(
                "file", "supplier-shifts-import-template.xlsx", XLSX,
                new ExcelTemplateService().buildShifts());

        assertThat(detector.detect(template)).contains(ImportBatchType.SHIFTS);
    }

    @Test
    @DisplayName("recognises supplier deduction template")
    void detectsDeductionTemplate() {
        MockMultipartFile template = new MockMultipartFile(
                "file", "supplier-deductions-template.xlsx", XLSX,
                new ExcelTemplateService().buildDeductions());

        assertThat(detector.detect(template)).contains(ImportBatchType.SUPPLIER_DEDUCTIONS);
    }

    @Test
    @DisplayName("a supplier deduction file passes requireNot on supplier deduction import screen")
    void allowsDeductionTemplateOnSupplierDeductionsImport() {
        MockMultipartFile template = new MockMultipartFile(
                "file", "supplier-deductions-template.xlsx", XLSX,
                new ExcelTemplateService().buildDeductions());

        assertThatCode(() -> detector.requireNot(template, ImportBatchType.CAPTAINS))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an unreadable file is left for the parser to report on")
    void unreadableFileIsNotClassified() {
        MockMultipartFile garbage = new MockMultipartFile(
                "file", "notes.xlsx", XLSX, "this is not a workbook".getBytes());

        assertThat(detector.detect(garbage)).isEqualTo(Optional.empty());
        assertThatCode(() -> detector.requireNot(garbage, ImportBatchType.SHIFTS))
                .doesNotThrowAnyException();
    }

    private MockMultipartFile ninjaReport() {
        try {
            byte[] content = new ClassPathResource("ninja_sample.xlsx").getContentAsByteArray();
            return new MockMultipartFile("file", "ninja_sample.xlsx", XLSX, content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MockMultipartFile captainTemplate() {
        return new MockMultipartFile("file", "captains-import-template.xlsx", XLSX,
                new ExcelTemplateService().buildCaptains());
    }
}
