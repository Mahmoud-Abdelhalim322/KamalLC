package com.kamal.lc.custody.service;

import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.custody.dto.CaptainCustodySummary;
import com.kamal.lc.custody.dto.CustodyRecordResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Exports custody data (all active items) as Excel workbooks with company branding.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustodyExportService {

    private final CustodyService custodyService;
    private final ExcelBranding branding;

    private static final String[] HEADERS = {
            "م", "ID-KSLSC", "اسم الكابتن", "نوع العهدة", "تفاصيل العهدة",
            "تاريخ التسليم", "الحالة", "ملاحظات"
    };

    public byte[] exportAllExcel() {
        List<CaptainCustodySummary> summaries = custodyService.getCaptainSummaries();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("العهد النشطة");
            sheet.setRightToLeft(true);

            int totalCols = HEADERS.length;

            // Column widths
            sheet.setColumnWidth(0, 5 * 256);
            sheet.setColumnWidth(1, 14 * 256);
            sheet.setColumnWidth(2, 28 * 256);
            sheet.setColumnWidth(3, 14 * 256);
            sheet.setColumnWidth(4, 30 * 256);
            sheet.setColumnWidth(5, 14 * 256);
            sheet.setColumnWidth(6, 10 * 256);
            sheet.setColumnWidth(7, 20 * 256);

            // White header style
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
            int r = branding.writeMasthead(sheet, totalCols, "سجل العهد النشطة",
                    "Active Custody Records", "");
            r++;

            // Header
            Row headerRow = sheet.createRow(r++);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data
            int seq = 1;
            int totalItems = 0;
            for (CaptainCustodySummary summary : summaries) {
                if (summary.getActiveItems() == null) continue;
                for (CustodyRecordResponse item : summary.getActiveItems()) {
                    Row row = sheet.createRow(r++);
                    int c = 0;

                    Cell seqCell = row.createCell(c++);
                    seqCell.setCellValue(seq++);
                    seqCell.setCellStyle(dataStyle);

                    Cell codeCell = row.createCell(c++);
                    codeCell.setCellValue(summary.getCaptainCode());
                    codeCell.setCellStyle(dataStyle);

                    Cell nameCell = row.createCell(c++);
                    nameCell.setCellValue(summary.getCaptainName());
                    nameCell.setCellStyle(nameStyle);

                    Cell typeCell = row.createCell(c++);
                    typeCell.setCellValue(item.getAssetTypeLabelAr() != null ? item.getAssetTypeLabelAr() : item.getAssetType());
                    typeCell.setCellStyle(dataStyle);

                    Cell labelCell = row.createCell(c++);
                    labelCell.setCellValue(item.getAssetLabel() != null ? item.getAssetLabel() : "—");
                    labelCell.setCellStyle(nameStyle);

                    Cell dateCell = row.createCell(c++);
                    dateCell.setCellValue(item.getAssignedAt() != null ? item.getAssignedAt() : "—");
                    dateCell.setCellStyle(dataStyle);

                    Cell statusCell = row.createCell(c++);
                    statusCell.setCellValue("ACTIVE".equals(item.getStatus()) ? "نشط" : "مُرجع");
                    statusCell.setCellStyle(dataStyle);

                    Cell notesCell = row.createCell(c++);
                    notesCell.setCellValue(item.getNotes() != null ? item.getNotes() : "");
                    notesCell.setCellStyle(dataStyle);

                    totalItems++;
                }
            }

            // Summary row
            Row summaryRow = sheet.createRow(r);
            Cell label = summaryRow.createCell(0);
            label.setCellValue("إجمالي العهد:");
            label.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 1));
            Cell count = summaryRow.createCell(2);
            count.setCellValue(totalItems);
            count.setCellStyle(headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build custody Excel export", e);
            throw new RuntimeException("فشل في تصدير ملف العهد", e);
        }
    }
}
