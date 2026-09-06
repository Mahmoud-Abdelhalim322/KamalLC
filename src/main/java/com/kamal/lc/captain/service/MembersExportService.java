package com.kamal.lc.captain.service;

import com.kamal.lc.captain.dto.CaptainResponse;
import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.common.export.ExcelStyles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Exports members (captains, staff, administrators) as Excel workbooks.
 * Uses white-only styling with Cairo font and company branding — no coloured backgrounds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembersExportService {

    private final CaptainService captainService;
    private final ExcelBranding branding;

    private static final String[] CAPTAIN_HEADERS = {
            "م", "ID-KSLSC", "الاسم", "نوع العقد", "ID-APP", "رقم الهاتف", "الجنسية",
            "حالة الوثائق", "الحالة التشغيلية", "تاريخ الالتحاق"
    };

    /**
     * Builds an Excel workbook for captains filtered by type.
     *
     * @param type {@code "EMPLOYEE"}, {@code "FREELANCE"}, or {@code null}/blank for all
     */
    public byte[] exportCaptainsExcel(String type) {
        List<CaptainResponse> captains = fetchAll(type);
        String title = resolveTitle(type);

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(title);
            sheet.setRightToLeft(true);

            int totalCols = CAPTAIN_HEADERS.length;

            // Column widths
            sheet.setColumnWidth(0, 5 * 256);   // م
            sheet.setColumnWidth(1, 14 * 256);  // ID-KSLSC
            sheet.setColumnWidth(2, 28 * 256);  // الاسم
            sheet.setColumnWidth(3, 12 * 256);  // نوع العقد
            sheet.setColumnWidth(4, 14 * 256);  // ID-APP
            sheet.setColumnWidth(5, 14 * 256);  // رقم الهاتف
            sheet.setColumnWidth(6, 12 * 256);  // الجنسية
            sheet.setColumnWidth(7, 14 * 256);  // حالة الوثائق
            sheet.setColumnWidth(8, 14 * 256);  // الحالة التشغيلية
            sheet.setColumnWidth(9, 14 * 256);  // تاريخ الالتحاق

            // White header style (no coloured backgrounds)
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            headerStyle.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            CellStyle nameStyle = workbook.createCellStyle();
            nameStyle.cloneStyleFrom(dataStyle);
            nameStyle.setAlignment(HorizontalAlignment.RIGHT);

            // Masthead
            int r = branding.writeMasthead(sheet, totalCols, title,
                    "Members Export Report", "");
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
            for (CaptainResponse captain : captains) {
                Row row = sheet.createRow(r++);
                int c = 0;

                Cell seqCell = row.createCell(c++);
                seqCell.setCellValue(seq++);
                seqCell.setCellStyle(dataStyle);

                Cell codeCell = row.createCell(c++);
                codeCell.setCellValue(captain.getKamalCode());
                codeCell.setCellStyle(dataStyle);

                Cell nameCell = row.createCell(c++);
                nameCell.setCellValue(captain.getFullNameAr());
                nameCell.setCellStyle(nameStyle);

                Cell typeCell = row.createCell(c++);
                typeCell.setCellValue("EMPLOYEE".equals(captain.getCaptainType()) ? "موظف" : "أجير");
                typeCell.setCellStyle(dataStyle);

                Cell ninjaCell = row.createCell(c++);
                ninjaCell.setCellValue(captain.getCurrentNinjaId() != null ? captain.getCurrentNinjaId() : "—");
                ninjaCell.setCellStyle(dataStyle);

                Cell phoneCell = row.createCell(c++);
                phoneCell.setCellValue(captain.getPhone() != null ? captain.getPhone() : "—");
                phoneCell.setCellStyle(dataStyle);

                Cell natCell = row.createCell(c++);
                natCell.setCellValue(captain.getNationality() != null ? captain.getNationality() : "—");
                natCell.setCellStyle(dataStyle);

                Cell docCell = row.createCell(c++);
                docCell.setCellValue(resolveDocAlert(captain.getDocumentAlert()));
                docCell.setCellStyle(dataStyle);

                Cell statusCell = row.createCell(c++);
                statusCell.setCellValue("ACTIVE".equals(captain.getStatus()) ? "نشط" : "متوقف");
                statusCell.setCellStyle(dataStyle);

                Cell joinCell = row.createCell(c++);
                joinCell.setCellValue(captain.getJoinDate() != null ? captain.getJoinDate() : "—");
                joinCell.setCellStyle(dataStyle);
            }

            // Summary row
            Row summaryRow = sheet.createRow(r);
            Cell summaryLabel = summaryRow.createCell(0);
            summaryLabel.setCellValue("إجمالي عدد الأعضاء:");
            summaryLabel.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 1));
            Cell summaryCount = summaryRow.createCell(2);
            summaryCount.setCellValue(captains.size());
            summaryCount.setCellStyle(headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build members Excel export", e);
            throw new RuntimeException("فشل في تصدير الملف", e);
        }
    }

    private List<CaptainResponse> fetchAll(String type) {
        // Fetch all captains in one page
        Pageable pageable = PageRequest.of(0, 10000, Sort.by("fullNameAr"));
        return captainService.getCaptains(null, type, null, pageable).getContent();
    }

    private String resolveTitle(String type) {
        if ("EMPLOYEE".equalsIgnoreCase(type)) return "قائمة الكباتن (KSC)";
        if ("FREELANCE".equalsIgnoreCase(type)) return "قائمة الكباتن (KSCFL)";
        return "قائمة جميع الكباتن";
    }

    private String resolveDocAlert(String alert) {
        if (alert == null) return "سليم";
        return switch (alert) {
            case "VALID" -> "سليم";
            case "EXPIRING_SOON" -> "قريب الانتهاء";
            case "EXPIRED" -> "منتهي";
            case "MISSING" -> "ناقص";
            default -> alert;
        };
    }
}
