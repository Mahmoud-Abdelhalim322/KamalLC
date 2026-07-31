package com.kamal.lc.payroll.service;

import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.payroll.dto.PayrollRunResponse;
import com.kamal.lc.payroll.dto.StaffAdjustmentResponse;
import com.kamal.lc.payroll.dto.StaffPayrollLineResponse;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollExportServiceTest {

    private final ExcelBranding branding = new ExcelBranding();
    private final PayrollExportService exportService = new PayrollExportService(branding);

    @Test
    void buildsStaffPayrollExcelWorkbookSuccessfully() throws Exception {
        PayrollRunResponse run = PayrollRunResponse.builder()
                .id(UUID.randomUUID())
                .periodFrom("2026-07-01")
                .periodTo("2026-07-31")
                .status("APPROVED")
                .title("مسير رواتب يوليو 2026")
                .build();

        StaffPayrollLineResponse line1 = StaffPayrollLineResponse.builder()
                .id(UUID.randomUUID())
                .staffId(UUID.randomUUID())
                .staffCode("KSLW001")
                .staffName("أحمد محمود")
                .staffType("WORKER")
                .jobTitle("عامل شحن")
                .basicSalary(BigDecimal.valueOf(3000))
                .housingAllowance(BigDecimal.valueOf(500))
                .otherAllowances(BigDecimal.valueOf(200))
                .gross(BigDecimal.valueOf(3700))
                .adjustmentsTotal(BigDecimal.valueOf(100))
                .net(BigDecimal.valueOf(3800))
                .notes("ملاحظات اختبار")
                .adjustments(List.of(
                        StaffAdjustmentResponse.builder()
                                .adjustmentType("REWARD")
                                .kind("BONUS")
                                .amount(BigDecimal.valueOf(100))
                                .signedAmount(BigDecimal.valueOf(100))
                                .build()
                ))
                .build();

        byte[] bytes = exportService.buildStaff(run, List.of(line1), "WORKER");
        assertThat(bytes).isNotEmpty();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("مسير العاملين");
            assertThat(sheet).isNotNull();
            assertThat(sheet.isRightToLeft()).isTrue();

            String text = textOf(sheet);
            assertThat(text)
                    .contains("ID-KSLSC")
                    .contains("KSLW001")
                    .contains("أحمد محمود")
                    .contains("عامل شحن")
                    .contains("المجموع");
        }
    }

    private static String textOf(Sheet sheet) {
        StringBuilder text = new StringBuilder();
        sheet.rowIterator().forEachRemaining(row -> row.cellIterator().forEachRemaining(cell -> {
            if (cell.getCellType() == CellType.STRING) {
                text.append(cell.getStringCellValue()).append('\n');
            }
        }));
        return text.toString();
    }
}
