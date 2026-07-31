package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.NinjaShiftRow;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the supplier's daily shift export (the "ساعات العمل" file from Ninja).
 *
 * <p>Header matching is tolerant in the same way as {@link ExcelParserService}: the
 * supplier renames columns between exports, so headers are normalised before lookup.
 * All timestamps in the file are bare Excel serial numbers with no timezone, and the
 * supplier reports in local time — they are read as Asia/Riyadh.
 */
@Slf4j
@Service
public class NinjaShiftParser {

    /** The supplier reports wall-clock KSA time; the shift's calendar day follows from it. */
    public static final ZoneId SUPPLIER_ZONE = ZoneId.of("Asia/Riyadh");

    private static final Map<String, String> COLUMN_MAP;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("supplier_id", "supplierExternalId");
        m.put("supplier id", "supplierExternalId");
        m.put("supplier_name", "supplierName");
        m.put("supplier name", "supplierName");
        m.put("shift_id", "externalShiftId");
        m.put("shift id", "externalShiftId");
        m.put("city", "city");
        m.put("zone_name", "zoneName");
        m.put("zone name", "zoneName");
        m.put("shiftname", "shiftName");
        m.put("shift name", "shiftName");
        m.put("captain_id", "ninjaCaptainId");
        m.put("captain id", "ninjaCaptainId");
        m.put("id - ninja", "ninjaCaptainId");
        m.put("id-ninja", "ninjaCaptainId");
        m.put("captain_name", "ninjaCaptainName");
        m.put("captain name", "ninjaCaptainName");
        m.put("shift start at", "scheduledStart");
        m.put("shift end at", "scheduledEnd");
        m.put("captain checked-in at", "actualCheckin");
        m.put("captain checked in at", "actualCheckin");
        m.put("flag - attendance", "attendanceFlag");
        m.put("flag-attendance", "attendanceFlag");
        m.put("flag", "attendanceFlag");
        m.put("delay time (minutes)", "delayMinutes");
        m.put("delay time", "delayMinutes");
        m.put("working hour (hour)", "workingHours");
        m.put("working hour", "workingHours");
        m.put("total time", "workingHours");
        m.put("shift type", "shiftTypeHours");
        m.put("total delivered", "totalDelivered");
        m.put("offline time", "offlineTime");
        m.put("free time", "freeTime");
        m.put("busy time", "busyTime");
        m.put("away from zone", "awayFromZone");
        m.put("busy utilization %", "busyUtilization");
        m.put("busy utilization", "busyUtilization");
        m.put("delivery ontime %", "deliveryOntime");
        m.put("delivery ontime", "deliveryOntime");
        m.put("hourly utr", "hourlyUtr");

        Map<String, String> normalized = new LinkedHashMap<>();
        m.forEach((key, field) -> normalized.put(ExcelCellSupport.normalizeHeader(key), field));
        COLUMN_MAP = Collections.unmodifiableMap(normalized);
    }

    /** Normalised header → field, exposed so {@link ImportFileDetector} can score a sheet. */
    static Map<String, String> columnMap() {
        return COLUMN_MAP;
    }

    public List<NinjaShiftRow> parse(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ExcelParserService.ExcelParseException("الملف لا يحتوي على أي أوراق عمل");
            }

            Map<String, Integer> cols = ExcelCellSupport.detectHeaders(sheet, COLUMN_MAP);
            requireColumn(cols, "externalShiftId", "shift_id");
            requireColumn(cols, "ninjaCaptainId", "captain_id");
            requireColumn(cols, "scheduledStart", "Shift Start At");

            int headerRowIdx = ExcelCellSupport.findHeaderRowIndex(sheet, COLUMN_MAP);
            List<NinjaShiftRow> rows = new ArrayList<>();

            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || ExcelCellSupport.isEmptyRow(row)) continue;

                String shiftId = ExcelCellSupport.text(row, cols, "externalShiftId");
                String captainId = ExcelCellSupport.text(row, cols, "ninjaCaptainId");
                if (shiftId == null && captainId == null) continue;

                List<String> warnings = new ArrayList<>();

                OffsetDateTime start = ExcelCellSupport.dateTime(row, cols, "scheduledStart", SUPPLIER_ZONE);
                if (start == null) {
                    warnings.add("تعذّرت قراءة تاريخ بداية الشفت");
                }

                rows.add(NinjaShiftRow.builder()
                        .rowNumber(i + 1)
                        .supplierExternalId(ExcelCellSupport.text(row, cols, "supplierExternalId"))
                        .supplierName(ExcelCellSupport.text(row, cols, "supplierName"))
                        .externalShiftId(shiftId)
                        .city(ExcelCellSupport.text(row, cols, "city"))
                        .zoneName(ExcelCellSupport.text(row, cols, "zoneName"))
                        .shiftName(ExcelCellSupport.text(row, cols, "shiftName"))
                        .ninjaCaptainId(captainId)
                        .ninjaCaptainName(ExcelCellSupport.text(row, cols, "ninjaCaptainName"))
                        .scheduledStart(start)
                        .scheduledEnd(ExcelCellSupport.dateTime(row, cols, "scheduledEnd", SUPPLIER_ZONE))
                        .actualCheckin(ExcelCellSupport.dateTime(row, cols, "actualCheckin", SUPPLIER_ZONE))
                        .shiftDate(start != null ? start.atZoneSameInstant(SUPPLIER_ZONE).toLocalDate() : null)
                        .attendanceFlag(ExcelCellSupport.text(row, cols, "attendanceFlag"))
                        .delayMinutes(ExcelCellSupport.decimal(row, cols, "delayMinutes"))
                        .workingHours(ExcelCellSupport.decimal(row, cols, "workingHours"))
                        .shiftTypeHours(ExcelCellSupport.decimal(row, cols, "shiftTypeHours"))
                        .totalDelivered(ExcelCellSupport.integer(row, cols, "totalDelivered"))
                        .offlineTime(ExcelCellSupport.decimal(row, cols, "offlineTime"))
                        .freeTime(ExcelCellSupport.decimal(row, cols, "freeTime"))
                        .busyTime(ExcelCellSupport.decimal(row, cols, "busyTime"))
                        .awayFromZone(ExcelCellSupport.decimal(row, cols, "awayFromZone"))
                        .busyUtilization(ExcelCellSupport.decimal(row, cols, "busyUtilization"))
                        .deliveryOntime(ExcelCellSupport.decimal(row, cols, "deliveryOntime"))
                        .hourlyUtr(ExcelCellSupport.decimal(row, cols, "hourlyUtr"))
                        .warnings(warnings)
                        .build());
            }

            // Runs over the whole batch, because a swapped date is only recognisable against
            // the rows that came through intact.
            ShiftDateRepair.repair(rows, SUPPLIER_ZONE);

            log.info("Parsed {} shift rows from '{}'", rows.size(), file.getOriginalFilename());
            return rows;

        } catch (ExcelParserService.ExcelParseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse shift file: {}", e.getMessage(), e);
            throw new ExcelParserService.ExcelParseException("فشل في قراءة الملف: " + e.getMessage());
        }
    }

    private void requireColumn(Map<String, Integer> cols, String field, String headerLabel) {
        if (!cols.containsKey(field)) {
            throw new ExcelParserService.ExcelParseException(
                    "عمود '" + headerLabel + "' غير موجود في الملف");
        }
    }
}
