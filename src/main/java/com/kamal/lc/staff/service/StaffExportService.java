package com.kamal.lc.staff.service;

import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports staff (workers or managers) as branded Excel workbooks with all entity fields.
 * Each type is exported separately.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffExportService {

    private final StaffRepository staffRepo;
    private final ExcelBranding branding;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] STAFF_HEADERS = {
            "م",
            // --- أساسي ---
            "الكود", "الوظيفة", "الاسم بالعربي", "الاسم بالإنجليزي",
            "الجنس", "الجنسية", "تاريخ الميلاد", "الهاتف",
            "مكان الإقامة", "العنوان", "البريد الإلكتروني", "APP ID",
            // --- الجواز ---
            "رقم الجواز", "تاريخ إصدار الجواز", "تاريخ انتهاء الجواز", "حالة الجواز",
            // --- الإقامة ---
            "رقم الإقامة", "المهنة بالإقامة", "تاريخ انتهاء الإقامة", "تاريخ تجديد الإقامة", "حالة الإقامة",
            // --- عقد قوى ---
            "رقم عقد قوى", "رقم المنشأة", "بداية عقد قوى", "نهاية عقد قوى", "حالة عقد قوى",
            // --- رخصة العمل ---
            "تاريخ انتهاء رخصة العمل", "حالة رخصة العمل",
            // --- الشركة ---
            "الشركة المنتسب إليها", "رقم السجل التجاري",
            // --- التأمين ---
            "رقم التأمين", "فئة التأمين", "تاريخ إصدار التأمين",
            "تاريخ انتهاء التأمين", "شركة التأمين", "حالة التأمين",
            // --- الرخصة ---
            "لديه رخصة", "تاريخ انتهاء الرخصة", "حالة الرخصة",
            // --- شخصي ---
            "فصيلة الدم", "فترة التجربة (يوم)",
            // --- البنك ---
            "رقم الحساب البنكي", "اسم البنك",
            // --- الرواتب ---
            "الراتب الأساسي", "بدل السكن", "بدلات أخرى", "بدل المواصلات",
            "بدل البنزين", "بدل الاتصالات والإنترنت", "بدل المكافأة",
            // --- العمل ---
            "هاتف العمل", "القسم", "الشعبة", "المشرف", "موقع العمل", "يعمل مع",
            // --- الحالة ---
            "الحالة", "تاريخ الالتحاق", "تاريخ المغادرة", "الملاحظات"
    };

    /**
     * Builds an Excel workbook for staff filtered by type (WORKER or MANAGER).
     */
    public byte[] exportStaffExcel(String staffType) {
        List<Staff> staffList = fetchAll(staffType);
        String title = resolveTitle(staffType);
        String typeLabel = resolveTypeLabel(staffType);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(title);
            sheet.setRightToLeft(true);

            int totalCols = STAFF_HEADERS.length;

            // Column widths
            sheet.setColumnWidth(0, 5 * 256);   // م
            for (int i = 1; i < totalCols; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            sheet.setColumnWidth(3, 28 * 256);  // الاسم بالعربي
            sheet.setColumnWidth(4, 24 * 256);  // الاسم بالإنجليزي
            sheet.setColumnWidth(56, 30 * 256); // الملاحظات

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle nameStyle = workbook.createCellStyle();
            nameStyle.cloneStyleFrom(dataStyle);
            nameStyle.setAlignment(HorizontalAlignment.RIGHT);
            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.cloneStyleFrom(dataStyle);
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            // Masthead
            int r = branding.writeMasthead(sheet, totalCols, title,
                    "تصدير بيانات " + typeLabel + " الشامل", "");
            // Export date row
            Row dateRow = sheet.createRow(r++);
            Cell dateCell = dateRow.createCell(0);
            dateCell.setCellValue("تاريخ التصدير: " + LocalDate.now().format(DATE_FMT));
            CellStyle dateStyle = workbook.createCellStyle();
            Font dateFont = workbook.createFont();
            dateFont.setBold(true);
            dateFont.setFontHeightInPoints((short) 10);
            dateStyle.setFont(dateFont);
            dateCell.setCellStyle(dateStyle);
            sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 5));
            r++;

            // Header row
            Row headerRow = sheet.createRow(r++);
            for (int i = 0; i < STAFF_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(STAFF_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int seq = 1;
            for (Staff s : staffList) {
                Row row = sheet.createRow(r++);
                int col = 0;

                addCell(row, col++, seq++, dataStyle);
                // --- أساسي ---
                addCell(row, col++, s.getStaffCode(), dataStyle);
                addCell(row, col++, s.getJobTitle(), dataStyle);
                addCell(row, col++, s.getFullNameAr(), nameStyle);
                addCell(row, col++, s.getFullNameEn(), dataStyle);
                addCell(row, col++, s.getGender(), dataStyle);
                addCell(row, col++, s.getNationality(), dataStyle);
                addCell(row, col++, fmtDate(s.getDateOfBirth()), dataStyle);
                addCell(row, col++, s.getPhone(), dataStyle);
                addCell(row, col++, s.getPlaceOfResidence(), dataStyle);
                addCell(row, col++, s.getHomeAddress(), dataStyle);
                addCell(row, col++, s.getEmail(), dataStyle);
                addCell(row, col++, s.getAppId(), dataStyle);
                // --- الجواز ---
                addCell(row, col++, s.getPassportNumber(), dataStyle);
                addCell(row, col++, fmtDate(s.getPassportIssueDate()), dataStyle);
                addCell(row, col++, fmtDate(s.getPassportExpiry()), dataStyle);
                addCell(row, col++, s.getPassportStatus(), dataStyle);
                // --- الإقامة ---
                addCell(row, col++, s.getIqamaNumber(), dataStyle);
                addCell(row, col++, s.getIqamaOccupation(), dataStyle);
                addCell(row, col++, fmtDate(s.getIqamaExpiry()), dataStyle);
                addCell(row, col++, fmtDate(s.getIqamaExpiryRenewed()), dataStyle);
                addCell(row, col++, s.getIqamaStatus(), dataStyle);
                // --- عقد قوى ---
                addCell(row, col++, s.getQiwaContractNumber(), dataStyle);
                addCell(row, col++, s.getEstablishmentNumber(), dataStyle);
                addCell(row, col++, fmtDate(s.getQiwaContractStart()), dataStyle);
                addCell(row, col++, fmtDate(s.getQiwaContractEnd()), dataStyle);
                addCell(row, col++, s.getQiwaContractStatus(), dataStyle);
                // --- رخصة العمل ---
                addCell(row, col++, fmtDate(s.getWorkPermitExpiry()), dataStyle);
                addCell(row, col++, s.getWorkPermitStatus(), dataStyle);
                // --- الشركة ---
                addCell(row, col++, s.getAffiliatedCompany(), dataStyle);
                addCell(row, col++, s.getCrNumber(), dataStyle);
                // --- التأمين ---
                addCell(row, col++, s.getInsuranceNumber(), dataStyle);
                addCell(row, col++, s.getInsuranceCategory(), dataStyle);
                addCell(row, col++, fmtDate(s.getInsuranceIssueDate()), dataStyle);
                addCell(row, col++, fmtDate(s.getInsuranceExpiry()), dataStyle);
                addCell(row, col++, s.getInsuranceCompany(), dataStyle);
                addCell(row, col++, s.getInsuranceStatus(), dataStyle);
                // --- الرخصة ---
                addCell(row, col++, s.isHasLicence() ? "نعم" : "لا", dataStyle);
                addCell(row, col++, fmtDate(s.getLicenceExpiry()), dataStyle);
                addCell(row, col++, s.getLicenceStatus(), dataStyle);
                // --- شخصي ---
                addCell(row, col++, s.getBloodType(), dataStyle);
                addCell(row, col++, s.getTrialPeriodDays() != null ? s.getTrialPeriodDays() : null, dataStyle);
                // --- البنك ---
                addCell(row, col++, s.getBankAccountNumber(), dataStyle);
                addCell(row, col++, s.getBankName(), dataStyle);
                // --- الرواتب ---
                addMoneyCell(row, col++, s.getBasicSalary(), moneyStyle);
                addMoneyCell(row, col++, s.getHousingAllowance(), moneyStyle);
                addMoneyCell(row, col++, s.getOtherAllowances(), moneyStyle);
                addMoneyCell(row, col++, s.getTransportationAllowance(), moneyStyle);
                addMoneyCell(row, col++, s.getFuelAllowanceAmount(), moneyStyle);
                addMoneyCell(row, col++, s.getCallInternetAllowance(), moneyStyle);
                addMoneyCell(row, col++, s.getRewardAllowance(), moneyStyle);
                // --- العمل ---
                addCell(row, col++, s.getWorkPhone(), dataStyle);
                addCell(row, col++, s.getDepartment(), dataStyle);
                addCell(row, col++, s.getSection(), dataStyle);
                addCell(row, col++, s.getSupervisor(), dataStyle);
                addCell(row, col++, s.getWorkLocation(), dataStyle);
                addCell(row, col++, s.getWorkWith(), dataStyle);
                // --- الحالة ---
                addCell(row, col++, statusLabel(s.getStatus()), dataStyle);
                addCell(row, col++, fmtDate(s.getJoinDate()), dataStyle);
                addCell(row, col++, fmtDate(s.getLeaveDate()), dataStyle);
                addCell(row, col++, s.getNotes(), dataStyle);
            }

            // Summary row
            Row summaryRow = sheet.createRow(r);
            Cell summaryLabel = summaryRow.createCell(0);
            summaryLabel.setCellValue("إجمالي عدد " + typeLabel + ":");
            summaryLabel.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 1));
            Cell summaryCount = summaryRow.createCell(2);
            summaryCount.setCellValue(staffList.size());
            summaryCount.setCellStyle(headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build staff Excel export for type {}", staffType, e);
            throw new RuntimeException("فشل في تصدير الملف", e);
        }
    }

    private List<Staff> fetchAll(String staffType) {
        if (staffType != null && !staffType.isBlank()) {
            return staffRepo.findByStaffType(staffType.toUpperCase(),
                    org.springframework.data.domain.PageRequest.of(0, 10000, Sort.by("fullNameAr"))).getContent();
        }
        return staffRepo.findAll(Sort.by("fullNameAr"));
    }

    private String resolveTitle(String staffType) {
        if ("WORKER".equalsIgnoreCase(staffType)) return "قائمة العاملين";
        if ("MANAGER".equalsIgnoreCase(staffType)) return "قائمة الإداريين";
        return "قائمة جميع الموظفين";
    }

    private String resolveTypeLabel(String staffType) {
        if ("WORKER".equalsIgnoreCase(staffType)) return "العاملين";
        if ("MANAGER".equalsIgnoreCase(staffType)) return "الإداريين";
        return "الموظفين";
    }

    private String statusLabel(String status) {
        if ("ACTIVE".equalsIgnoreCase(status)) return "نشط";
        if ("INACTIVE".equalsIgnoreCase(status)) return "غير نشط";
        if ("TERMINATED".equalsIgnoreCase(status)) return "منتهي";
        return status;
    }

    private String fmtDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "";
    }

    private void addCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value instanceof Number num) {
            cell.setCellValue(num.doubleValue());
        } else {
            cell.setCellValue(value != null ? value.toString() : "");
        }
        cell.setCellStyle(style);
    }

    private void addMoneyCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
