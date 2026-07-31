package com.kamal.lc.imports.service;

import com.kamal.lc.imports.dto.ExcelCaptainRow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelParserServiceTest {

    private final ExcelParserService parser = new ExcelParserService();

    @Test
    void readsEveryColumnOfTheCaptainSheet() {
        MultipartFile file = sheet(
                new String[]{"ID-KSLSC", "ID - NINJA", "اسم الكابتن", "الهاتف", "الجنسية",
                        "تاريخ الميلاد", "البريد الالكتروني", "رقم الإقامة", "انتهاء الإقامة",
                        "رقم الجواز", "انتهاء الجواز", "رقم الرخصة", "انتهاء الرخصة"},
                new String[]{"ksc0001", "1234567", "محمد أحمد", "0501234567", "مصري",
                        "1990-05-12", "captain@example.com", "2412345678", "2026-08-30",
                        "A12345678", "2028-01-15", "L9876543", "2027-03-01"});

        ExcelCaptainRow row = parser.parse(file).get(0);

        assertThat(row.getKamalCode()).isEqualTo("KSC0001");
        assertThat(row.getNinjaId()).isEqualTo("1234567");
        assertThat(row.getCaptainName()).isEqualTo("محمد أحمد");
        assertThat(row.getPhone()).isEqualTo("0501234567");
        assertThat(row.getNationality()).isEqualTo("مصري");
        assertThat(row.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 12));
        assertThat(row.getEmail()).isEqualTo("captain@example.com");
        assertThat(row.getIqamaNumber()).isEqualTo("2412345678");
        assertThat(row.getIqamaExpiry()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(row.getPassportNumber()).isEqualTo("A12345678");
        assertThat(row.getPassportExpiry()).isEqualTo(LocalDate.of(2028, 1, 15));
        assertThat(row.getDrivingLicenceNo()).isEqualTo("L9876543");
        assertThat(row.getLicenceExpiry()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(row.getWarnings()).isEmpty();
    }

    @Test
    void matchesHeadersRegardlessOfHamzaSpellingAndSpacing() {
        MultipartFile file = sheet(
                new String[]{"id-kslsc", "ID-NINJA", "اسم  الكابتن", "رقم الاقامة", "تاريخ انتهاء الاقامه"},
                new String[]{"KSC0002", "7654321", "علي حسن", "2400000001", "31/12/2026"});

        ExcelCaptainRow row = parser.parse(file).get(0);

        assertThat(row.getCaptainName()).isEqualTo("علي حسن");
        assertThat(row.getIqamaNumber()).isEqualTo("2400000001");
        assertThat(row.getIqamaExpiry()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void readsRealExcelDateCells() {
        MultipartFile file = sheetWithDateCell(LocalDate.of(1988, 2, 29));

        ExcelCaptainRow row = parser.parse(file).get(0);

        assertThat(row.getDateOfBirth()).isEqualTo(LocalDate.of(1988, 2, 29));
    }

    @Test
    void readsArabicIndicDigitsInDates() {
        MultipartFile file = sheet(
                new String[]{"ID-KSLSC", "ID - NINJA", "اسم الكابتن", "تاريخ الميلاد"},
                new String[]{"KSC0003", "1112223", "سعيد عمر", "١٩٩٥-٠٧-٠٩"});

        ExcelCaptainRow row = parser.parse(file).get(0);

        assertThat(row.getDateOfBirth()).isEqualTo(LocalDate.of(1995, 7, 9));
    }

    @Test
    void warnsInsteadOfFailingOnAnUnreadableDate() {
        MultipartFile file = sheet(
                new String[]{"ID-KSLSC", "ID - NINJA", "اسم الكابتن", "انتهاء الجواز"},
                new String[]{"KSC0004", "9998887", "خالد سالم", "قريبا"});

        ExcelCaptainRow row = parser.parse(file).get(0);

        assertThat(row.getPassportExpiry()).isNull();
        assertThat(row.getKamalCode()).isEqualTo("KSC0004");
        assertThat(row.getWarnings()).hasSize(1);
        assertThat(row.getWarnings().get(0)).contains("انتهاء الجواز").contains("قريبا");
    }

    @Test
    void optionalColumnsMayBeAbsentEntirely() {
        MultipartFile file = sheet(
                new String[]{"ID-KSLSC", "ID - NINJA", "اسم الكابتن"},
                new String[]{"KSC0005", "5556667", "عمر ياسر"});

        ExcelCaptainRow row = parser.parse(file).get(0);

        assertThat(row.getPhone()).isNull();
        assertThat(row.getIqamaExpiry()).isNull();
        assertThat(row.getWarnings()).isEmpty();
    }

    @Test
    void rejectsFilesMissingARequiredColumn() {
        MultipartFile file = sheet(
                new String[]{"ID-KSLSC", "اسم الكابتن"},
                new String[]{"KSC0006", "ماجد فهد"});

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(ExcelParserService.ExcelParseException.class)
                .hasMessageContaining("ID - NINJA");
    }

    // ---------- helpers ----------

    private MultipartFile sheet(String[] headers, String[] values) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("captains");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            Row dataRow = sheet.createRow(1);
            for (int i = 0; i < values.length; i++) {
                dataRow.createCell(i).setCellValue(values[i]);
            }

            workbook.write(out);
            return new MockMultipartFile("file", "captains.xlsx", null, out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Builds a sheet whose birth-date cell is a genuine date-formatted numeric cell. */
    private MultipartFile sheetWithDateCell(LocalDate birthDate) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("captains");
            Row headerRow = sheet.createRow(0);
            List<String> headers = List.of("ID-KSLSC", "ID - NINJA", "اسم الكابتن", "تاريخ الميلاد");
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }

            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));

            Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("KSC0007");
            dataRow.createCell(1).setCellValue("4443332");
            dataRow.createCell(2).setCellValue("ياسر منصور");
            Cell dateCell = dataRow.createCell(3);
            dateCell.setCellValue(birthDate);
            dateCell.setCellStyle(dateStyle);

            workbook.write(out);
            return new MockMultipartFile("file", "captains.xlsx", null, out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
