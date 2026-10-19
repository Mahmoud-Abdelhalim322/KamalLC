package com.kamal.lc.changelog.service;

import com.kamal.lc.changelog.dto.ChangeLogFilter;
import com.kamal.lc.changelog.dto.ChangeLogResponse;
import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.common.export.ExcelStyles;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChangeLogExportService {

    private final ChangeLogService changeLogService;
    private final ExcelBranding branding;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Map<String, String> ENTITY_TYPE_LABELS = Map.of(
            "SHIFT", "الشيفتات",
            "SUPPLIER_ID", "سجل المعرفات",
            "VEHICLE", "أسطول السيارات",
            "ASSET_ITEM", "المخزن",
            "CAPTAIN", "الكباتن"
    );

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("UPDATE", "تعديل"),
            Map.entry("EXCLUDE", "استبعاد"),
            Map.entry("RESTORE", "إرجاع"),
            Map.entry("LINK", "ربط"),
            Map.entry("UNLINK", "فك ربط"),
            Map.entry("CREATE", "إنشاء"),
            Map.entry("DELETE", "حذف"),
            Map.entry("EXCLUDE_DEDUCTION", "استبعاد خصم"),
            Map.entry("RESTORE_DEDUCTION", "إرجاع خصم")
    );

    public byte[] exportExcel(ChangeLogFilter filter) {
        List<ChangeLogResponse> rows = changeLogService.searchAll(filter,
                Sort.by(Sort.Direction.DESC, "changedAt"));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("سجل التغييرات");
            sheet.setRightToLeft(true);

            String[] headers = {"التاريخ", "القسم", "العملية", "الكيان", "الحقل",
                    "القيمة القديمة", "القيمة الجديدة", "المسؤول", "السبب"};

            // Write the branded masthead
            String periodStr = "";
            if (filter.getFrom() != null && filter.getTo() != null) {
                periodStr = "الفترة: " + filter.getFrom() + " إلى " + filter.getTo();
            }
            int startRow = branding.writeMasthead(sheet, headers.length, "تقرير حركة التغييرات", periodStr);

            CellStyle headerStyle = ExcelStyles.header(wb);
            CellStyle dataStyle = wb.createCellStyle(); // plain style

            Row headerRow = sheet.createRow(startRow);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = startRow + 1;
            for (ChangeLogResponse r : rows) {
                Row row = sheet.createRow(rowNum++);
                createCell(row, 0, r.getChangedAt() != null ? r.getChangedAt().format(DT_FMT) : "", dataStyle);
                createCell(row, 1, ENTITY_TYPE_LABELS.getOrDefault(r.getEntityType(), r.getEntityType()), dataStyle);
                createCell(row, 2, ACTION_LABELS.getOrDefault(r.getAction(), r.getAction()), dataStyle);
                createCell(row, 3, nvl(r.getEntityLabel()), dataStyle);
                createCell(row, 4, nvl(r.getFieldLabel()), dataStyle);
                createCell(row, 5, nvl(r.getOldValue()), dataStyle);
                createCell(row, 6, nvl(r.getNewValue()), dataStyle);
                createCell(row, 7, nvl(r.getChangedBy()), dataStyle);
                createCell(row, 8, nvl(r.getChangeReason()), dataStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("فشل تصدير سجل التغييرات", e);
        }
    }

    public byte[] exportPdf(ChangeLogFilter filter) {
        List<ChangeLogResponse> rows = changeLogService.searchAll(filter,
                Sort.by(Sort.Direction.DESC, "changedAt"));

        return buildChangeLogPdf(rows, filter);
    }

    private byte[] buildChangeLogPdf(List<ChangeLogResponse> rows, ChangeLogFilter filter) {
        try (var doc = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4.rotate())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            com.lowagie.text.pdf.PdfWriter.getInstance(doc, out);
            doc.open();

            // Load Arabic font
            com.lowagie.text.pdf.BaseFont bf;
            try {
                bf = com.lowagie.text.pdf.BaseFont.createFont(
                        "/fonts/NotoSansArabic-Regular.ttf",
                        com.lowagie.text.pdf.BaseFont.IDENTITY_H,
                        com.lowagie.text.pdf.BaseFont.EMBEDDED);
            } catch (Exception e) {
                bf = com.lowagie.text.pdf.BaseFont.createFont(
                        com.lowagie.text.pdf.BaseFont.HELVETICA,
                        com.lowagie.text.pdf.BaseFont.WINANSI,
                        com.lowagie.text.pdf.BaseFont.NOT_EMBEDDED);
            }

            com.lowagie.text.Font titleFont = new com.lowagie.text.Font(bf, 16, com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font cellFont = new com.lowagie.text.Font(bf, 8);

            com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("تقرير حركة التغييرات", titleFont);
            title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            doc.add(title);

            // Filter summary
            StringBuilder filterText = new StringBuilder();
            if (filter.getEntityType() != null)
                filterText.append("القسم: ").append(ENTITY_TYPE_LABELS.getOrDefault(filter.getEntityType(), filter.getEntityType())).append("  ");
            if (filter.getFrom() != null) filterText.append("من: ").append(filter.getFrom()).append("  ");
            if (filter.getTo() != null) filterText.append("إلى: ").append(filter.getTo()).append("  ");
            if (filter.getChangedBy() != null) filterText.append("المسؤول: ").append(filter.getChangedBy()).append("  ");
            if (!filterText.isEmpty()) {
                com.lowagie.text.Paragraph filterPara = new com.lowagie.text.Paragraph(filterText.toString(), cellFont);
                filterPara.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                filterPara.setSpacingAfter(8);
                doc.add(filterPara);
            }

            com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(9);
            table.setWidthPercentage(100);
            table.setRunDirection(com.lowagie.text.pdf.PdfWriter.RUN_DIRECTION_RTL);
            table.setWidths(new float[]{10, 8, 7, 12, 10, 15, 15, 10, 13});

            String[] headers = {"التاريخ", "القسم", "العملية", "الكيان", "الحقل", "القيمة القديمة", "القيمة الجديدة", "المسؤول", "السبب"};
            java.awt.Color headerBg = new java.awt.Color(33, 37, 41);
            com.lowagie.text.Font whiteHeaderFont = new com.lowagie.text.Font(bf, 9, com.lowagie.text.Font.BOLD, java.awt.Color.WHITE);
            for (String h : headers) {
                com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(
                        new com.lowagie.text.Phrase(h, whiteHeaderFont));
                cell.setBackgroundColor(headerBg);
                cell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                cell.setPadding(5);
                cell.setRunDirection(com.lowagie.text.pdf.PdfWriter.RUN_DIRECTION_RTL);
                table.addCell(cell);
            }

            boolean alternate = false;
            java.awt.Color altBg = new java.awt.Color(248, 249, 250);
            for (ChangeLogResponse r : rows) {
                java.awt.Color bg = alternate ? altBg : java.awt.Color.WHITE;
                addPdfCell(table, r.getChangedAt() != null ? r.getChangedAt().format(DT_FMT) : "", cellFont, bg);
                addPdfCell(table, ENTITY_TYPE_LABELS.getOrDefault(r.getEntityType(), r.getEntityType()), cellFont, bg);
                addPdfCell(table, ACTION_LABELS.getOrDefault(r.getAction(), r.getAction()), cellFont, bg);
                addPdfCell(table, nvl(r.getEntityLabel()), cellFont, bg);
                addPdfCell(table, nvl(r.getFieldLabel()), cellFont, bg);
                addPdfCell(table, nvl(r.getOldValue()), cellFont, bg);
                addPdfCell(table, nvl(r.getNewValue()), cellFont, bg);
                addPdfCell(table, nvl(r.getChangedBy()), cellFont, bg);
                addPdfCell(table, nvl(r.getChangeReason()), cellFont, bg);
                alternate = !alternate;
            }

            doc.add(table);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("فشل تصدير تقرير التغييرات PDF", e);
        }
    }

    private void addPdfCell(com.lowagie.text.pdf.PdfPTable table, String text,
                            com.lowagie.text.Font font, java.awt.Color bg) {
        com.lowagie.text.pdf.PdfPCell cell = new com.lowagie.text.pdf.PdfPCell(
                new com.lowagie.text.Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(4);
        cell.setRunDirection(com.lowagie.text.pdf.PdfWriter.RUN_DIRECTION_RTL);
        cell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }
}
