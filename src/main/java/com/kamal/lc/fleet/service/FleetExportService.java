package com.kamal.lc.fleet.service;

import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.fleet.dto.VehicleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Exports fleet vehicles as Excel workbooks with company branding.
 * White-only styling matching the members export pattern.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FleetExportService {

    private final FleetService fleetService;
    private final ExcelBranding branding;

    private static final String[] VEHICLE_HEADERS = {
            "م", "كود المركبة", "رقم اللوحة", "نوع الملكية", "الماركة", "الطراز",
            "سنة الصنع", "اللون", "العداد", "الحالة", "وكيل التأجير",
            "الكابتن المستلم", "رقم التسجيل", "VIN"
    };

    public byte[] exportVehiclesExcel(String ownershipFilter) {
        List<VehicleResponse> vehicles = fleetService.listVehicles(
                null, null, ownershipFilter, null,
                PageRequest.of(0, 10000, Sort.by("plateNumber"))
        ).getContent();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("الأسطول");
            sheet.setRightToLeft(true);

            int totalCols = VEHICLE_HEADERS.length;

            // Column widths
            sheet.setColumnWidth(0, 5 * 256);
            sheet.setColumnWidth(1, 14 * 256);
            sheet.setColumnWidth(2, 14 * 256);
            sheet.setColumnWidth(3, 12 * 256);
            sheet.setColumnWidth(4, 12 * 256);
            sheet.setColumnWidth(5, 12 * 256);
            sheet.setColumnWidth(6, 10 * 256);
            sheet.setColumnWidth(7, 10 * 256);
            sheet.setColumnWidth(8, 10 * 256);
            sheet.setColumnWidth(9, 10 * 256);
            sheet.setColumnWidth(10, 16 * 256);
            sheet.setColumnWidth(11, 22 * 256);
            sheet.setColumnWidth(12, 14 * 256);
            sheet.setColumnWidth(13, 20 * 256);

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

            // Masthead
            int r = branding.writeMasthead(sheet, totalCols, "قائمة الأسطول",
                    "Fleet Vehicles Export", "");
            r++;

            // Headers
            Row headerRow = sheet.createRow(r++);
            for (int i = 0; i < VEHICLE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(VEHICLE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data
            int seq = 1;
            for (VehicleResponse v : vehicles) {
                Row row = sheet.createRow(r++);
                int c = 0;
                setCell(row, c++, seq++, dataStyle);
                setCell(row, c++, v.getVehicleCode(), dataStyle);
                setCell(row, c++, v.getPlateNumber(), dataStyle);
                setCell(row, c++, translateOwnership(v.getOwnershipType()), dataStyle);
                setCell(row, c++, v.getBrand(), dataStyle);
                setCell(row, c++, v.getModel(), dataStyle);
                setCell(row, c++, v.getYear() != null ? v.getYear().toString() : "—", dataStyle);
                setCell(row, c++, v.getColor(), dataStyle);
                setCell(row, c++, v.getOdometer() != null ? v.getOdometer().toString() : "—", dataStyle);
                setCell(row, c++, translateStatus(v.getStatus()), dataStyle);
                setCell(row, c++, v.getRentalAgentName(), dataStyle);
                setCell(row, c++, v.getCurrentCaptainName(), dataStyle);
                setCell(row, c++, v.getRegistrationNumber(), dataStyle);
                setCell(row, c++, v.getVin(), dataStyle);
            }

            // Summary
            Row summaryRow = sheet.createRow(r);
            Cell label = summaryRow.createCell(0);
            label.setCellValue("إجمالي المركبات:");
            label.setCellStyle(headerStyle);
            sheet.addMergedRegion(new CellRangeAddress(r, r, 0, 1));
            Cell count = summaryRow.createCell(2);
            count.setCellValue(vehicles.size());
            count.setCellStyle(headerStyle);

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build fleet Excel export", e);
            throw new RuntimeException("فشل في تصدير ملف الأسطول", e);
        }
    }

    private void setCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(value != null ? value.toString() : "—");
        }
        cell.setCellStyle(style);
    }

    private String translateOwnership(String type) {
        if (type == null) return "—";
        return switch (type) {
            case "OWNED" -> "مملوكة";
            case "RENTED" -> "مؤجرة";
            case "FINANCED" -> "تمويلية";
            default -> type;
        };
    }

    private String translateStatus(String status) {
        if (status == null) return "—";
        return switch (status) {
            case "AVAILABLE" -> "متاحة";
            case "ASSIGNED" -> "مسلّمة";
            case "IN_MAINTENANCE" -> "صيانة";
            case "DECOMMISSIONED" -> "خارج الخدمة";
            default -> status;
        };
    }
}
