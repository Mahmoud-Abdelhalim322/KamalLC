package com.kamal.lc.imports.service;

import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.imports.dto.DeductionImportPreviewResponse;
import com.kamal.lc.imports.repository.ImportBatchRepository;
import com.kamal.lc.shift.repository.ShiftRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierDeductionImportServiceTest {

    @Mock
    private ShiftRepository shiftRepo;

    @Mock
    private CaptainRepository captainRepo;

    @Mock
    private ImportBatchRepository batchRepo;

    @Mock
    private ImportFileDetector fileDetector;

    @InjectMocks
    private SupplierDeductionImportService importService;

    @Test
    @DisplayName("correctly detects discount amount and reason columns from supplier deduction template")
    void previewDeductions_successWithTemplateHeaders() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("خصومات المورد");

        // Row 0: Headers
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID - NINJA");
        headerRow.createCell(1).setCellValue("ID-KSLSC");
        headerRow.createCell(2).setCellValue("تاريخ الشيفت");
        headerRow.createCell(3).setCellValue("سعر الخصم");
        headerRow.createCell(4).setCellValue("اسم الكابتن");
        headerRow.createCell(5).setCellValue("سبب الخصم");

        // Row 1: Data
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("95516");
        dataRow.createCell(1).setCellValue("");
        dataRow.createCell(2).setCellValue("2026-06-01 06:00:00");
        dataRow.createCell(3).setCellValue(50);
        dataRow.createCell(4).setCellValue("علي");
        dataRow.createCell(5).setCellValue("خصم تأخير عن الشيفت");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        MockMultipartFile file = new MockMultipartFile(
                "file", "نموذج-استيراد-خصومات-المورد.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baos.toByteArray());

        when(shiftRepo.findByNinjaCaptainIdAndShiftDate(eq("95516"), eq(LocalDate.of(2026, 6, 1))))
                .thenReturn(Collections.emptyList());
        when(batchRepo.save(any())).thenAnswer(invocation -> {
            com.kamal.lc.imports.entity.ImportBatch b = invocation.getArgument(0);
            if (b.getId() == null) b.setId(java.util.UUID.randomUUID());
            return b;
        });

        DeductionImportPreviewResponse preview = importService.previewDeductions(file);

        assertThat(preview.getTotalRows()).isEqualTo(1);
        assertThat(preview.getRows()).hasSize(1);

        DeductionImportPreviewResponse.DeductionRowPreview rowPreview = preview.getRows().get(0);
        assertThat(rowPreview.getNinjaId()).isEqualTo("95516");
        assertThat(rowPreview.getShiftDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(rowPreview.getAmount()).isEqualByComparingTo("50");
        assertThat(rowPreview.getReason()).isEqualTo("خصم تأخير عن الشيفت");
    }

    @Test
    @DisplayName("previewDeductions converts negative amounts to positive absolute values")
    void previewDeductions_handlesNegativeAmount() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("خصومات المورد");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID - NINJA");
        headerRow.createCell(1).setCellValue("تاريخ الشيفت");
        headerRow.createCell(2).setCellValue("سعر الخصم");

        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("95516");
        dataRow.createCell(1).setCellValue("2026-06-01");
        dataRow.createCell(2).setCellValue(-50);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        MockMultipartFile file = new MockMultipartFile(
                "file", "deductions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baos.toByteArray());

        com.kamal.lc.shift.entity.Shift shift = com.kamal.lc.shift.entity.Shift.builder().build();
        when(shiftRepo.findByNinjaCaptainIdAndShiftDate(eq("95516"), eq(LocalDate.of(2026, 6, 1))))
                .thenReturn(Collections.singletonList(shift));
        when(batchRepo.save(any())).thenAnswer(invocation -> {
            com.kamal.lc.imports.entity.ImportBatch b = invocation.getArgument(0);
            if (b.getId() == null) b.setId(java.util.UUID.randomUUID());
            return b;
        });

        DeductionImportPreviewResponse preview = importService.previewDeductions(file);

        assertThat(preview.getTotalRows()).isEqualTo(1);
        assertThat(preview.getMatchedRows()).isEqualTo(1);
        assertThat(preview.getTotalDeductionAmount()).isEqualByComparingTo("50");
        assertThat(preview.getRows().get(0).getAmount()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("commitDeductions accumulates imported deduction onto existing shift supplierDeduction")
    void commitDeductions_accumulatesOnExistingDeduction() throws Exception {
        com.kamal.lc.shift.entity.Shift shift = com.kamal.lc.shift.entity.Shift.builder()
                .supplierDeduction(new BigDecimal("100"))
                .build();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("خصومات المورد");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("ID - NINJA");
        headerRow.createCell(1).setCellValue("تاريخ الشيفت");
        headerRow.createCell(2).setCellValue("سعر الخصم");

        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("95516");
        dataRow.createCell(1).setCellValue("2026-06-01");
        dataRow.createCell(2).setCellValue(-50);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        MockMultipartFile file = new MockMultipartFile(
                "file", "deductions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", baos.toByteArray());

        java.util.UUID batchId = java.util.UUID.randomUUID();
        com.kamal.lc.imports.entity.ImportBatch batch = com.kamal.lc.imports.entity.ImportBatch.builder()
                .id(batchId)
                .batchType(com.kamal.lc.imports.entity.ImportBatchType.SUPPLIER_DEDUCTIONS)
                .status("PREVIEW")
                .build();

        when(batchRepo.findById(batchId)).thenReturn(java.util.Optional.of(batch));
        when(shiftRepo.findByNinjaCaptainIdAndShiftDate(eq("95516"), eq(LocalDate.of(2026, 6, 1))))
                .thenReturn(Collections.singletonList(shift));

        DeductionImportPreviewResponse response = importService.commitDeductions(batchId, file);

        assertThat(response.getMatchedRows()).isEqualTo(1);
        assertThat(shift.getSupplierDeduction()).isEqualByComparingTo("150");
    }
}
