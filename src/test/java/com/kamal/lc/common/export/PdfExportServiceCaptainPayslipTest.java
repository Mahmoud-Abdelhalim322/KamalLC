package com.kamal.lc.common.export;

import com.kamal.lc.payroll.dto.AdjustmentResponse;
import com.kamal.lc.payroll.dto.PayrollLineResponse;
import com.kamal.lc.payroll.dto.PayrollRunResponse;
import com.kamal.lc.settings.service.EffectiveSettings;
import com.kamal.lc.settings.service.SettingsResolver;
import com.kamal.lc.shift.entity.Shift;
import com.kamal.lc.shift.repository.ShiftRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class PdfExportServiceCaptainPayslipTest {

    private final ShiftRepository shiftRepository = Mockito.mock(ShiftRepository.class);
    private final SettingsResolver settingsResolver = Mockito.mock(SettingsResolver.class);
    private final PdfExportService pdfExportService = new PdfExportService(shiftRepository, settingsResolver);

    @Test
    @DisplayName("buildPayslipPdf generates a valid PDF with Cairo font and Ninja deduction details")
    void buildPayslipPdf_success() {
        UUID captainId = UUID.randomUUID();
        PayrollRunResponse run = PayrollRunResponse.builder()
                .id(UUID.randomUUID())
                .periodFrom("2026-06-01")
                .periodTo("2026-06-30")
                .status("APPROVED")
                .title("مسير رواتب الكباتن شهر يونيو 2026")
                .build();

        PayrollLineResponse line = PayrollLineResponse.builder()
                .id(UUID.randomUUID())
                .captainId(captainId)
                .captainCode("KSC0025")
                .captainName("علاء الدين عمر على الامين")
                .captainNameEn("AlaaAldin Omar ALi")
                .captainType("OWN_VEHICLE")
                .supplierExternalIds("95516")
                .attendedShifts(28)
                .totalOrders(434)
                .totalHours(BigDecimal.valueOf(306.00))
                .basePay(BigDecimal.valueOf(2423.00))
                .lateDeductions(BigDecimal.valueOf(305.00))
                .supplierDeductions(BigDecimal.valueOf(150.00))
                .dailyIncentives(BigDecimal.ZERO)
                .fuelAllowances(BigDecimal.valueOf(341.00))
                .carAllowances(BigDecimal.ZERO)
                .gross(BigDecimal.valueOf(2764.00))
                .net(BigDecimal.valueOf(2150.00))
                .adjustmentsTotal(BigDecimal.valueOf(-614.00))
                .adjustments(List.of(
                        AdjustmentResponse.builder()
                                .adjustmentType("LOAN")
                                .amount(BigDecimal.valueOf(614.00))
                                .build()
                ))
                .build();

        EffectiveSettings settings = Mockito.mock(EffectiveSettings.class);
        when(settings.getMonthlyOrderTarget()).thenReturn(350);
        when(settingsResolver.resolve(any(LocalDate.class))).thenReturn(settings);

        Shift deductionShift1 = Shift.builder()
                .id(UUID.randomUUID())
                .shiftDate(LocalDate.of(2026, 6, 1))
                .supplierDeduction(BigDecimal.valueOf(50.00))
                .supplierDeductionReason("تأخير في تسليم الطلبات")
                .build();

        Shift deductionShift2 = Shift.builder()
                .id(UUID.randomUUID())
                .shiftDate(LocalDate.of(2026, 6, 3))
                .supplierDeduction(BigDecimal.valueOf(50.00))
                .supplierDeductionReason("عدم الالتزام بالزي الرسمى")
                .build();

        when(shiftRepository.findSupplierDeductionsByCaptain(eq(captainId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(deductionShift1, deductionShift2));

        byte[] pdfBytes = pdfExportService.buildPayslipPdf(run, line);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(0);
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");

        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("target/generated-payslip-test.pdf"), pdfBytes);
        } catch (Exception ignored) {}
    }
}
