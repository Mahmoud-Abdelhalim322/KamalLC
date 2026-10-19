package com.kamal.lc.payroll.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.common.export.ExcelStyles;
import com.kamal.lc.payroll.dto.AdjustmentResponse;
import com.kamal.lc.payroll.dto.PayrollLineResponse;
import com.kamal.lc.payroll.dto.PayrollRunResponse;
import com.kamal.lc.payroll.dto.StaffAdjustmentResponse;
import com.kamal.lc.payroll.dto.StaffPayrollLineResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * Renders a payroll run as the Excel sheet Kamal already works with
 * ("مسيرات رواتب شهر"), so the system's output can be compared against the old one
 * column by column during the changeover.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollExportService {

    private final ExcelBranding branding;

    /** Column order follows the organisational form layout, with extra analytical columns appended. */
    private static final List<String> HEADERS = List.of(
            "ID-KSLSC",
            "ID - NINJA",
            "اسم الكابتن\nCAPTAIN NAME",
            "النوع\nTYPE",
            "عدد الشفتات\nTOTAL SHIFT",
            "أيام الغياب\nABSENT",
            "اجمالي الطلبات\nTotal Delivered",
            "اجمالي الساعات\nTotal Working Hour",
            "نسبة الانشغال\nBusy Utilization %",
            "تحقيق التارقيت\nACHIEVE THE TARGET",
            "خصم تأخير\nDeduction for late",
            "خصم نينجا\nNinja Deduction",
            "عمولة بعد التارقت 18 طلب\nAfter TARGET",
            "حوافز يومية\nDAILY INCENTIVE",
            "بدل سيارة\nCAR ALLOWANCE",
            "تحقيق الساعات\nTOTAL TARGET HOUR",
            "SUM of الطلبات\nTOTAL SALARY ORDER",
            "عمولة الإجازة\nVACATION COMMISSION",
            "مجموع الراتب\nTOTAL SALARY",
            "بدل سكن\nHousing allowance",
            "مكافأة\nREWARD",
            "فروقات البنزين\nGasoline price differences",
            "غرامة نينجا\nNinja Penalty",
            "سلف\nLOAN",
            "صافي الراتب\nNET SALARY",
            "إيراد المورد\nSUPPLIER REVENUE",
            "الهامش\nMARGIN");

    private static final List<String> STAFF_HEADERS = List.of(
            "ID-KSLSC",
            "اسم الموظف\nEMPLOYEE NAME",
            "الوظيفة\nJOB TITLE",
            "النوع\nTYPE",
            "الراتب الأساسي\nBASIC SALARY",
            "بدل سكن\nHOUSING ALLOWANCE",
            "بدلات أخرى\nOTHER ALLOWANCES",
            "مجموع الراتب\nGROSS SALARY",
            "مكافأة\nREWARD",
            "إضافة أخرى\nOTHER BONUS",
            "غرامة نينجا\nNinja Penalty",
            "سلف\nLOAN",
            "خصم آخر\nOTHER DEDUCTION",
            "إجمالي البنود اليدوية\nTOTAL ADJUSTMENTS",
            "صافي الراتب\nNET SALARY",
            "ملاحظات\nNOTES");

    public byte[] build(PayrollRunResponse run, List<PayrollLineResponse> lines) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("مسير الرواتب");
            sheet.setRightToLeft(true);
            for (int i = 0; i < HEADERS.size(); i++) {
                sheet.setColumnWidth(i, 15 * 256);
            }

            CellStyle headerStyle = ExcelStyles.header(workbook);
            CellStyle moneyStyle = ExcelStyles.money(workbook);
            CellStyle intStyle = ExcelStyles.integer(workbook);
            CellStyle percentStyle = ExcelStyles.percent(workbook);
            CellStyle totalStyle = ExcelStyles.total(workbook);

            int r = branding.writeMasthead(sheet, HEADERS.size(),
                    run.getTitle() != null ? run.getTitle() : "مسير الرواتب",
                    "الفترة من " + run.getPeriodFrom() + " إلى " + run.getPeriodTo()
                            + "   —   الحالة: " + statusLabel(run.getStatus()));
            r++; // blank spacer row

            int headerRowIdx = r;
            Row headerRow = sheet.createRow(r++);
            headerRow.setHeightInPoints(38);
            for (int i = 0; i < HEADERS.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS.get(i));
                cell.setCellStyle(headerStyle);
            }

            for (PayrollLineResponse line : lines) {
                Row row = sheet.createRow(r++);
                int c = 0;
                text(row, c++, line.getCaptainCode());                          // 0: ID-KSLSC
                text(row, c++, line.getSupplierExternalIds());                  // 1: ID - NINJA
                text(row, c++, line.getCaptainName());                          // 2: اسم الكابتن
                text(row, c++, typeLabel(line.getCaptainType()));               // 3: النوع
                number(row, c++, BigDecimal.valueOf(line.getAttendedShifts()), intStyle); // 4: عدد الشفتات
                number(row, c++, BigDecimal.valueOf(line.getAbsentShifts()), intStyle);   // 5: أيام الغياب
                number(row, c++, BigDecimal.valueOf(line.getTotalOrders()), intStyle);    // 6: اجمالي الطلبات
                number(row, c++, line.getTotalHours(), moneyStyle);             // 7: اجمالي الساعات
                number(row, c++, line.getAvgBusyUtilization(), percentStyle);   // 8: نسبة الانشغال
                number(row, c++, line.getTargetBonus(), moneyStyle);            // 9: تحقيق التارقيت
                number(row, c++, line.getLateDeductions(), moneyStyle);         // 10: خصم تأخير
                number(row, c++, line.getSupplierDeductions(), moneyStyle);     // 11: خصم نينجا
                number(row, c++, line.getExtraOrderPay(), moneyStyle);          // 12: عمولة بعد التارقت 18 طلب
                number(row, c++, line.getDailyIncentives(), moneyStyle);        // 13: حوافز يومية
                number(row, c++, line.getCarAllowances(), moneyStyle);          // 14: بدل سيارة
                number(row, c++, line.getTotalHours(), moneyStyle);             // 15: تحقيق الساعات
                number(row, c++, line.getBasePay(), moneyStyle);                // 16: SUM of الطلبات
                number(row, c++, line.getVacationCommission(), moneyStyle);     // 17: عمولة الإجازة
                number(row, c++, line.getGross(), moneyStyle);                  // 18: مجموع الراتب
                number(row, c++, adjustment(line, "HOUSING_ALLOWANCE"), moneyStyle); // 19: بدل سكن
                number(row, c++, adjustment(line, "REWARD"), moneyStyle);       // 20: مكافأة
                number(row, c++, adjustment(line, "FUEL_DIFFERENCE"), moneyStyle);   // 21: فروقات البنزين
                number(row, c++, adjustment(line, "SUPPLIER_PENALTY"), moneyStyle);  // 22: غرامة نينجا
                number(row, c++, adjustment(line, "LOAN"), moneyStyle);         // 23: سلف
                number(row, c++, line.getNet(), moneyStyle);                    // 24: صافي الراتب
                number(row, c++, line.getSupplierRevenueTotal(), moneyStyle);   // 25: إيراد المورد
                number(row, c, line.getMargin(), moneyStyle);                   // 26: الهامش
            }

            writeTotalsRow(sheet, r, lines, totalStyle);

            sheet.createFreezePane(3, headerRowIdx + 1);
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to build the payroll export: {}", e.getMessage(), e);
            throw new BusinessException("EXPORT_ERROR", "فشل في إنشاء ملف المسير");
        }
    }

    private void writeTotalsRow(Sheet sheet, int rowIdx, List<PayrollLineResponse> lines, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        Cell label = row.createCell(0);
        label.setCellValue("المجموع");
        label.setCellStyle(style);

        // Column index → how to pull the value out of a line; keeps the totals row in step
        // with the data columns above it.
        record Total(int column, Function<PayrollLineResponse, BigDecimal> field) {}
        List<Total> totals = List.of(
                new Total(4, l -> BigDecimal.valueOf(l.getAttendedShifts())),
                new Total(5, l -> BigDecimal.valueOf(l.getAbsentShifts())),
                new Total(6, l -> BigDecimal.valueOf(l.getTotalOrders())),
                new Total(7, PayrollLineResponse::getTotalHours),
                new Total(9, PayrollLineResponse::getTargetBonus),
                new Total(10, PayrollLineResponse::getLateDeductions),
                new Total(11, PayrollLineResponse::getSupplierDeductions),
                new Total(12, PayrollLineResponse::getExtraOrderPay),
                new Total(13, PayrollLineResponse::getDailyIncentives),
                new Total(14, PayrollLineResponse::getCarAllowances),
                new Total(16, PayrollLineResponse::getBasePay),
                new Total(17, PayrollLineResponse::getVacationCommission),
                new Total(18, PayrollLineResponse::getGross),
                new Total(24, PayrollLineResponse::getNet),
                new Total(25, PayrollLineResponse::getSupplierRevenueTotal),
                new Total(26, PayrollLineResponse::getMargin));

        for (Total total : totals) {
            BigDecimal sum = lines.stream().map(total.field())
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Cell cell = row.createCell(total.column());
            cell.setCellValue(sum.doubleValue());
            cell.setCellStyle(style);
        }
    }

    /** Sums one adjustment type for a line — each named type gets its own sheet column. */
    private BigDecimal adjustment(PayrollLineResponse line, String type) {
        if (line.getAdjustments() == null) return BigDecimal.ZERO;
        return line.getAdjustments().stream()
                .filter(a -> type.equals(a.getAdjustmentType()))
                .map(AdjustmentResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void text(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    private static void number(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() : 0d);
        cell.setCellStyle(style);
    }

    private static String typeLabel(String captainType) {
        return "FREELANCE".equalsIgnoreCase(captainType) ? "KSCFL" : "KSC";
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "APPROVED" -> "معتمد";
            case "PAID" -> "مدفوع";
            default -> "مسودة";
        };
    }

    public byte[] buildStaff(PayrollRunResponse run, List<StaffPayrollLineResponse> lines, String staffType) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            String sheetName = "WORKER".equalsIgnoreCase(staffType) ? "مسير العاملين"
                    : "MANAGER".equalsIgnoreCase(staffType) ? "مسير الإداريين" : "مسير الموظفين";

            Sheet sheet = workbook.createSheet(sheetName);
            sheet.setRightToLeft(true);
            for (int i = 0; i < STAFF_HEADERS.size(); i++) {
                sheet.setColumnWidth(i, 16 * 256);
            }

            CellStyle headerStyle = ExcelStyles.header(workbook);
            CellStyle moneyStyle = ExcelStyles.money(workbook);
            CellStyle totalStyle = ExcelStyles.total(workbook);

            String mainTitle = "WORKER".equalsIgnoreCase(staffType) ? "مسير رواتب العاملين"
                    : "MANAGER".equalsIgnoreCase(staffType) ? "مسير رواتب الإداريين" : "مسير رواتب الموظفين";

            if (run.getTitle() != null && !run.getTitle().isBlank()) {
                mainTitle = run.getTitle() + " — " + ( "WORKER".equalsIgnoreCase(staffType) ? "العاملين" : "MANAGER".equalsIgnoreCase(staffType) ? "الإداريين" : "الموظفين" );
            }

            int r = branding.writeMasthead(sheet, STAFF_HEADERS.size(),
                    mainTitle,
                    "الفترة من " + run.getPeriodFrom() + " إلى " + run.getPeriodTo()
                            + "   —   الحالة: " + statusLabel(run.getStatus()));
            r++; // blank spacer row

            int headerRowIdx = r;
            Row headerRow = sheet.createRow(r++);
            headerRow.setHeightInPoints(38);
            for (int i = 0; i < STAFF_HEADERS.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(STAFF_HEADERS.get(i));
                cell.setCellStyle(headerStyle);
            }

            for (StaffPayrollLineResponse line : lines) {
                Row row = sheet.createRow(r++);
                int c = 0;
                text(row, c++, line.getStaffCode());
                text(row, c++, line.getStaffName());
                text(row, c++, line.getJobTitle());
                text(row, c++, staffTypeLabel(line.getStaffType()));
                number(row, c++, line.getBasicSalary(), moneyStyle);
                number(row, c++, line.getHousingAllowance(), moneyStyle);
                number(row, c++, line.getOtherAllowances(), moneyStyle);
                number(row, c++, line.getGross(), moneyStyle);
                number(row, c++, staffAdjustment(line, "REWARD"), moneyStyle);
                number(row, c++, staffAdjustment(line, "OTHER_BONUS"), moneyStyle);
                number(row, c++, staffAdjustment(line, "SUPPLIER_PENALTY"), moneyStyle);
                number(row, c++, staffAdjustment(line, "LOAN"), moneyStyle);
                number(row, c++, staffAdjustment(line, "OTHER_DEDUCTION"), moneyStyle);
                number(row, c++, line.getAdjustmentsTotal(), moneyStyle);
                number(row, c++, line.getNet(), moneyStyle);
                text(row, c, line.getNotes());
            }

            writeStaffTotalsRow(sheet, r, lines, totalStyle);

            sheet.createFreezePane(3, headerRowIdx + 1);
            workbook.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Failed to build the staff payroll export: {}", e.getMessage(), e);
            throw new BusinessException("EXPORT_ERROR", "فشل في إنشاء ملف مسير الموظفين");
        }
    }

    private void writeStaffTotalsRow(Sheet sheet, int rowIdx, List<StaffPayrollLineResponse> lines, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        Cell label = row.createCell(0);
        label.setCellValue("المجموع");
        label.setCellStyle(style);

        record Total(int column, Function<StaffPayrollLineResponse, BigDecimal> field) {}
        List<Total> totals = List.of(
                new Total(4, StaffPayrollLineResponse::getBasicSalary),
                new Total(5, StaffPayrollLineResponse::getHousingAllowance),
                new Total(6, StaffPayrollLineResponse::getOtherAllowances),
                new Total(7, StaffPayrollLineResponse::getGross),
                new Total(8, l -> staffAdjustment(l, "REWARD")),
                new Total(9, l -> staffAdjustment(l, "OTHER_BONUS")),
                new Total(10, l -> staffAdjustment(l, "SUPPLIER_PENALTY")),
                new Total(11, l -> staffAdjustment(l, "LOAN")),
                new Total(12, l -> staffAdjustment(l, "OTHER_DEDUCTION")),
                new Total(13, StaffPayrollLineResponse::getAdjustmentsTotal),
                new Total(14, StaffPayrollLineResponse::getNet));

        for (Total total : totals) {
            BigDecimal sum = lines.stream().map(total.field())
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Cell cell = row.createCell(total.column());
            cell.setCellValue(sum.doubleValue());
            cell.setCellStyle(style);
        }
    }

    private BigDecimal staffAdjustment(StaffPayrollLineResponse line, String type) {
        if (line.getAdjustments() == null) return BigDecimal.ZERO;
        return line.getAdjustments().stream()
                .filter(a -> type.equalsIgnoreCase(a.getAdjustmentType()))
                .map(StaffAdjustmentResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String staffTypeLabel(String staffType) {
        if ("WORKER".equalsIgnoreCase(staffType)) return "عامل";
        if ("MANAGER".equalsIgnoreCase(staffType)) return "إداري";
        return staffType != null ? staffType : "";
    }

}
