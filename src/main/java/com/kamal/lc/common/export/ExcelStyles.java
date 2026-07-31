package com.kamal.lc.common.export;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * The cell styles the exported workbooks share, so every sheet the system produces
 * reads the same way. Each call creates a style on the given workbook — build them
 * once per workbook and reuse the instances across its rows.
 */
public final class ExcelStyles {

    public static final String MONEY_FORMAT = "#,##0.00";
    public static final String INT_FORMAT = "#,##0";
    public static final String PERCENT_FORMAT = "0.0%";

    private ExcelStyles() {
    }

    /** The company name across the top of the sheet. */
    public static CellStyle companyName(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    /** What the sheet is and which period it covers — the lines under the company name. */
    public static CellStyle title(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 13);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    /** Heading that opens a block of rows inside the sheet. */
    public static CellStyle section(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    public static CellStyle header(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    /** The name side of a label/value row. */
    public static CellStyle label(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        return style;
    }

    public static CellStyle money(Workbook workbook) {
        return number(workbook, MONEY_FORMAT);
    }

    public static CellStyle integer(Workbook workbook) {
        return number(workbook, INT_FORMAT);
    }

    public static CellStyle percent(Workbook workbook) {
        return number(workbook, PERCENT_FORMAT);
    }

    public static CellStyle number(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }

    /** The totals row that closes a table. */
    public static CellStyle total(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setDataFormat(workbook.createDataFormat().getFormat(MONEY_FORMAT));
        style.setBorderTop(BorderStyle.DOUBLE);
        return style;
    }
}
