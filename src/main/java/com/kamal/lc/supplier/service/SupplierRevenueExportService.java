package com.kamal.lc.supplier.service;

import com.kamal.lc.supplier.dto.SupplierRevenueDtos.SupplierRevenueSummaryResponse;
import com.kamal.lc.supplier.dto.SupplierRevenueDtos.SupplierRowSummary;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

@Service
public class SupplierRevenueExportService {

    public byte[] exportSummary(SupplierRevenueSummaryResponse summary) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("إيراد الموردين");
            sheet.setRightToLeft(true);

            // Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            CellStyle currencyStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            currencyStyle.setDataFormat(format.getFormat("#,##0.00"));

            CellStyle boldCurrencyStyle = workbook.createCellStyle();
            boldCurrencyStyle.setDataFormat(format.getFormat("#,##0.00"));
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldCurrencyStyle.setFont(boldFont);

            // Title Row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("تقرير إيرادات الموردين - للفترة من " + summary.getPeriodFrom() + " إلى " + summary.getPeriodTo());
            titleCell.setCellStyle(titleStyle);

            // Headers
            String[] headers = {
                    "كود المورد", "اسم المورد", "عدد الشيفتات", "إجمالي الطلبات",
                    "ساعات العمل", "الإيراد الأساسي", "الإيراد الإضافي",
                    "إجمالي الإضافات", "إجمالي الخصومات", "صافي الإيراد"
            };

            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data Rows
            int rowIdx = 3;
            for (SupplierRowSummary row : summary.getRows()) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(row.getSupplierCode());
                r.createCell(1).setCellValue(row.getSupplierName());
                r.createCell(2).setCellValue(row.getTotalShifts());
                r.createCell(3).setCellValue(row.getTotalDelivered());

                Cell c4 = r.createCell(4); c4.setCellValue(row.getWorkingHours().doubleValue()); c4.setCellStyle(currencyStyle);
                Cell c5 = r.createCell(5); c5.setCellValue(row.getBaseRevenue().doubleValue()); c5.setCellStyle(currencyStyle);
                Cell c6 = r.createCell(6); c6.setCellValue(row.getExtraRevenue().doubleValue()); c6.setCellStyle(currencyStyle);
                Cell c7 = r.createCell(7); c7.setCellValue(row.getAdditionsTotal().doubleValue()); c7.setCellStyle(currencyStyle);
                Cell c8 = r.createCell(8); c8.setCellValue(row.getDeductionsTotal().doubleValue()); c8.setCellStyle(currencyStyle);
                Cell c9 = r.createCell(9); c9.setCellValue(row.getNetRevenue().doubleValue()); c9.setCellStyle(boldCurrencyStyle);
            }

            // Totals Row
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(0).setCellValue("الإجمالي العام");
            totalRow.getCell(0).setCellStyle(titleStyle);
            totalRow.createCell(2).setCellValue(summary.getTotalShifts());
            totalRow.createCell(3).setCellValue(summary.getTotalOrders());

            Cell c9 = totalRow.createCell(9);
            c9.setCellValue(summary.getNetRevenue().doubleValue());
            c9.setCellStyle(boldCurrencyStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("فشل في إنشاء ملف Excel للإيرادات", e);
        }
    }
}
