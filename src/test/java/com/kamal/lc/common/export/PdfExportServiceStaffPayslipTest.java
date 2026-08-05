package com.kamal.lc.common.export;

import com.kamal.lc.payroll.dto.PayrollRunResponse;
import com.kamal.lc.payroll.dto.StaffAdjustmentResponse;
import com.kamal.lc.payroll.dto.StaffPayrollLineResponse;
import com.kamal.lc.settings.service.SettingsResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PdfExportServiceStaffPayslipTest {

    private final com.kamal.lc.shift.repository.ShiftRepository shiftRepository = Mockito.mock(com.kamal.lc.shift.repository.ShiftRepository.class);
    private final SettingsResolver settingsResolver = Mockito.mock(SettingsResolver.class);
    private final PdfExportService pdfExportService = new PdfExportService(shiftRepository, settingsResolver);

    @Test
    @DisplayName("buildStaffPayslipPdf generates a non-empty PDF byte array for single staff member")
    void buildStaffPayslipPdf_success() {
        PayrollRunResponse run = PayrollRunResponse.builder()
                .id(UUID.randomUUID())
                .periodFrom("2026-06-01")
                .periodTo("2026-06-30")
                .status("APPROVED")
                .title("مسيرات الرواتب شهر يونيو 6-2026")
                .build();

        StaffPayrollLineResponse line = StaffPayrollLineResponse.builder()
                .id(UUID.randomUUID())
                .staffId(UUID.randomUUID())
                .staffCode("KSM0006")
                .staffName("احمد سعيد عبدالباسط بدوي")
                .staffType("WORKER")
                .jobTitle("عامل")
                .basicSalary(BigDecimal.valueOf(5000))
                .housingAllowance(BigDecimal.valueOf(500))
                .otherAllowances(BigDecimal.valueOf(1000))
                .gross(BigDecimal.valueOf(1916.66))
                .net(BigDecimal.valueOf(1050.00))
                .adjustmentsTotal(BigDecimal.valueOf(-866.66))
                .adjustments(List.of(
                        StaffAdjustmentResponse.builder()
                                .adjustmentType("LOAN")
                                .kind("DEDUCTION")
                                .amount(BigDecimal.valueOf(866.66))
                                .signedAmount(BigDecimal.valueOf(-866.66))
                                .build()
                ))
                .build();

        byte[] pdfBytes = pdfExportService.buildStaffPayslipPdf(run, line);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(0);
        // PDF header magic bytes %PDF
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("buildBulkStaffPayslipsPdf generates a valid PDF containing all staff pages")
    void buildBulkStaffPayslipsPdf_success() {
        PayrollRunResponse run = PayrollRunResponse.builder()
                .id(UUID.randomUUID())
                .periodFrom("2026-06-01")
                .periodTo("2026-06-30")
                .status("APPROVED")
                .title("مسيرات الرواتب شهر يونيو 6-2026")
                .build();

        StaffPayrollLineResponse line1 = StaffPayrollLineResponse.builder()
                .id(UUID.randomUUID())
                .staffId(UUID.randomUUID())
                .staffCode("KSM0006")
                .staffName("احمد سعيد")
                .staffType("WORKER")
                .basicSalary(BigDecimal.valueOf(5000))
                .gross(BigDecimal.valueOf(5000))
                .net(BigDecimal.valueOf(4500))
                .build();

        StaffPayrollLineResponse line2 = StaffPayrollLineResponse.builder()
                .id(UUID.randomUUID())
                .staffId(UUID.randomUUID())
                .staffCode("KSM0007")
                .staffName("محمود علي")
                .staffType("MANAGER")
                .basicSalary(BigDecimal.valueOf(8000))
                .gross(BigDecimal.valueOf(8000))
                .net(BigDecimal.valueOf(7500))
                .build();

        byte[] bulkPdfBytes = pdfExportService.buildBulkStaffPayslipsPdf(run, List.of(line1, line2));

        assertThat(bulkPdfBytes).isNotNull();
        assertThat(bulkPdfBytes.length).isGreaterThan(0);
        assertThat(new String(bulkPdfBytes, 0, 4)).isEqualTo("%PDF");
    }
}
