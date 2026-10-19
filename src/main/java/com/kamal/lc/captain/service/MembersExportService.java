package com.kamal.lc.captain.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.export.ExcelBranding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports captains as branded Excel workbooks with all entity fields.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembersExportService {

    private final CaptainRepository captainRepo;
    private final ExcelBranding branding;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] CAPTAIN_HEADERS = {
            "م",
            // --- أساسي ---
            "الكود", "نوع العقد", "الاسم بالعربي", "الاسم بالإنجليزي", "الوظيفة",
            "الجنس", "الجنسية", "تاريخ الميلاد", "الهاتف", "البريد الإلكتروني",
            "مكان الإقامة", "العنوان", "APP ID",
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
            // --- الرخصة ---
            "رقم الرخصة", "تاريخ انتهاء الرخصة", "لديه رخصة", "حالة الرخصة",
            // --- التأمين ---
            "رقم التأمين", "فئة التأمين", "تاريخ إصدار التأمين", "شركة التأمين",
            "تاريخ انتهاء التأمين", "حالة التأمين",
            // --- شخصي ---
            "فصيلة الدم", "فترة التجربة (يوم)",
            // --- البنك ---
            "رقم الحساب البنكي", "اسم البنك",
            // --- الرواتب ---
            "الراتب الأساسي", "بدل السكن", "بدل المواصلات", "بدل البنزين",
            "بدل الاتصالات والإنترنت", "بدل المكافأة",
            // --- المركبة ---
            "يستخدم سيارته الخاصة", "مستحق بدل بنزين",
            // --- العمل ---
            "هاتف العمل", "القسم", "الشعبة", "المشرف", "موقع العمل", "يعمل مع",
            // --- الحالة ---
            "الحالة", "تاريخ الالتحاق", "تاريخ المغادرة", "الملاحظات"
    };

    /**
     * Builds an Excel workbook for captains filtered by type.
     */
    public byte[] exportCaptainsExcel(String type) {
        List<Captain> captains = fetchAll(type);
        String title = resolveTitle(type);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(title);
            sheet.setRightToLeft(true);

            int totalCols = CAPTAIN_HEADERS.length;

            // Column widths
            sheet.setColumnWidth(0, 5 * 256);   // م
            for (int i = 1; i < totalCols; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            // Wider for name columns
            sheet.setColumnWidth(3, 28 * 256);  // الاسم بالعربي
            sheet.setColumnWidth(4, 24 * 256);  // الاسم بالإنجليزي
            sheet.setColumnWidth(63, 30 * 256); // الملاحظات

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
                    "تصدير بيانات الكباتن الشامل", "");
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
            for (int i = 0; i < CAPTAIN_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(CAPTAIN_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int seq = 1;
            for (Captain c : captains) {
                Row row = sheet.createRow(r++);
                int col = 0;

                addCell(row, col++, seq++, dataStyle);
                // --- أساسي ---
                addCell(row, col++, c.getKamalCode(), dataStyle);
                addCell(row, col++, captainTypeLabel(c.getCaptainType()), dataStyle);
                addCell(row, col++, c.getFullNameAr(), nameStyle);
                addCell(row, col++, c.getFullNameEn(), dataStyle);
                addCell(row, col++, c.getJobTitle(), dataStyle);
                addCell(row, col++, c.getGender(), dataStyle);
                addCell(row, col++, c.getNationality(), dataStyle);
                addCell(row, col++, fmtDate(c.getDateOfBirth()), dataStyle);
                addCell(row, col++, c.getPhone(), dataStyle);
                addCell(row, col++, c.getEmail(), dataStyle);
                addCell(row, col++, c.getPlaceOfResidence(), dataStyle);
                addCell(row, col++, c.getHomeAddress(), dataStyle);
                addCell(row, col++, c.getAppId(), dataStyle);
                // --- الجواز ---
                addCell(row, col++, c.getPassportNumber(), dataStyle);
                addCell(row, col++, fmtDate(c.getPassportIssueDate()), dataStyle);
                addCell(row, col++, fmtDate(c.getPassportExpiry()), dataStyle);
                addCell(row, col++, c.getPassportStatus(), dataStyle);
                // --- الإقامة ---
                addCell(row, col++, c.getIqamaNumber(), dataStyle);
                addCell(row, col++, c.getIqamaOccupation(), dataStyle);
                addCell(row, col++, fmtDate(c.getIqamaExpiry()), dataStyle);
                addCell(row, col++, fmtDate(c.getIqamaExpiryRenewed()), dataStyle);
                addCell(row, col++, c.getIqamaStatus(), dataStyle);
                // --- عقد قوى ---
                addCell(row, col++, c.getQiwaContractNumber(), dataStyle);
                addCell(row, col++, c.getEstablishmentNumber(), dataStyle);
                addCell(row, col++, fmtDate(c.getQiwaContractStart()), dataStyle);
                addCell(row, col++, fmtDate(c.getQiwaContractEnd()), dataStyle);
                addCell(row, col++, c.getQiwaContractStatus(), dataStyle);
                // --- رخصة العمل ---
                addCell(row, col++, fmtDate(c.getWorkPermitExpiry()), dataStyle);
                addCell(row, col++, c.getWorkPermitStatus(), dataStyle);
                // --- الشركة ---
                addCell(row, col++, c.getAffiliatedCompany(), dataStyle);
                addCell(row, col++, c.getCrNumber(), dataStyle);
                // --- الرخصة ---
                addCell(row, col++, c.getDrivingLicenceNo(), dataStyle);
                addCell(row, col++, fmtDate(c.getLicenceExpiry()), dataStyle);
                addCell(row, col++, c.isHasLicence() ? "نعم" : "لا", dataStyle);
                addCell(row, col++, c.getLicenceStatus(), dataStyle);
                // --- التأمين ---
                addCell(row, col++, c.getInsuranceNumber(), dataStyle);
                addCell(row, col++, c.getInsuranceCategory(), dataStyle);
                addCell(row, col++, fmtDate(c.getInsuranceIssueDate()), dataStyle);
                addCell(row, col++, c.getInsuranceCompany(), dataStyle);
                addCell(row, col++, fmtDate(c.getInsuranceExpiry()), dataStyle);
                addCell(row, col++, c.getInsuranceStatus(), dataStyle);
                // --- شخصي ---
                addCell(row, col++, c.getBloodType(), dataStyle);
                addCell(row, col++, c.getTrialPeriodDays() != null ? c.getTrialPeriodDays() : null, dataStyle);
                // --- البنك ---
                addCell(row, col++, c.getBankAccountNumber(), dataStyle);
                addCell(row, col++, c.getBankName(), dataStyle);
                // --- الرواتب ---
                addMoneyCell(row, col++, c.getBasicSalary(), moneyStyle);
                addMoneyCell(row, col++, c.getHousingAllowance(), moneyStyle);
                addMoneyCell(row, col++, c.getTransportationAllowance(), moneyStyle);
                addMoneyCell(row, col++, c.getFuelAllowanceAmount(), moneyStyle);
                addMoneyCell(row, col++, c.getCallInternetAllowance(), moneyStyle);
                addMoneyCell(row, col++, c.getRewardAllowance(), moneyStyle);
                // --- المركبة ---
                addCell(row, col++, c.isUsesOwnVehicle() ? "نعم" : "لا", dataStyle);
                addCell(row, col++, c.isFuelAllowanceEligible() ? "نعم" : "لا", dataStyle);
                // --- العمل ---
                addCell(row, col++, c.getWorkPhone(), dataStyle);
                addCell(row, col++, c.getDepartment(), dataStyle);
                addCell(row, col++, c.getSection(), dataStyle);
                addCell(row, col++, c.getSupervisor(), dataStyle);
                addCell(row, col++, c.getWorkLocation(), dataStyle);
                addCell(row, col++, c.getWorkWith(), dataStyle);
                // --- الحالة ---
                addCell(row, col++, statusLabel(c.getStatus()), dataStyle);
                addCell(row, col++, fmtDate(c.getJoinDate()), dataStyle);
                addCell(row, col++, fmtDate(c.getLeaveDate()), dataStyle);
                addCell(row, col++, c.getNotes(), dataStyle);
            }

            // Summary row
            Row summaryRow = sheet.createRow(r);
            Cell summaryLabel = summaryRow.createCell(0);
            summaryLabel.setCellValue("إجمالي عدد الكباتن:");
            summaryLabel.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 1));
            Cell summaryCount = summaryRow.createCell(2);
            summaryCount.setCellValue(captains.size());
            summaryCount.setCellStyle(headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build captains Excel export", e);
            throw new RuntimeException("فشل في تصدير الملف", e);
        }
    }

    private List<Captain> fetchAll(String type) {
        if (type != null && !type.isBlank()) {
            return captainRepo.findByCaptainType(type.toUpperCase(),
                    org.springframework.data.domain.PageRequest.of(0, 10000, Sort.by("fullNameAr"))).getContent();
        }
        return captainRepo.findAll(Sort.by("fullNameAr"));
    }

    private String resolveTitle(String type) {
        if ("EMPLOYEE".equalsIgnoreCase(type)) return "قائمة الكباتن (KSC)";
        if ("FREELANCE".equalsIgnoreCase(type)) return "قائمة الكباتن (KSCFL)";
        return "قائمة جميع الكباتن";
    }

    private String captainTypeLabel(String type) {
        if ("EMPLOYEE".equalsIgnoreCase(type)) return "موظف";
        if ("FREELANCE".equalsIgnoreCase(type)) return "أجير";
        return type;
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

    private void addMoneyCell(Row row, int col, java.math.BigDecimal value, CellStyle style) {
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
