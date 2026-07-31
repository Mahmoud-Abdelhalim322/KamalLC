package com.kamal.lc.common.export;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.Units;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * The masthead every exported workbook opens with: the company logo pinned to the top
 * corner, the company name centred across the sheet's full width, and the lines that
 * say what the sheet is and which period it covers.
 *
 * <p>Call this before writing anything else into the sheet, and set the column widths
 * first — the logo is anchored in real column/row space, so it is only sized correctly
 * once the columns it sits over have their final widths.
 */
@Slf4j
@Component
public class ExcelBranding {

    public static final String COMPANY_NAME = "شركة كمال بن خلف السلمي للخدمات اللوجستية";

    private static final String LOGO_RESOURCE = "branding/logo-ksclc.png";
    /** Native size of the logo file — the anchor keeps this ratio whatever the columns are. */
    private static final double LOGO_ASPECT = 1063d / 680d;
    private static final int LOGO_HEIGHT_PX = 76;
    private static final int LOGO_MARGIN_PX = 4;

    /** Rows the logo band spans, and their height — together they have to clear the logo. */
    private static final int BAND_ROWS = 4;
    private static final short BAND_ROW_HEIGHT_POINTS = 18;
    private static final double PIXELS_PER_POINT = 96d / 72d;

    /** Read once and reused; a missing file must not fail an export, so it degrades to name-only. */
    private volatile byte[] logo;
    private volatile boolean logoLoaded;

    /**
     * Writes the logo band, then the report title and any extra lines under it.
     *
     * @param columnCount how wide the sheet's own table is — the block is centred over it
     * @return the first free row after the block
     */
    public int writeMasthead(Sheet sheet, int columnCount, String title, String... lines) {
        Workbook workbook = sheet.getWorkbook();
        int r = 0;

        for (int i = 0; i < BAND_ROWS; i++) {
            sheet.createRow(r + i).setHeightInPoints(BAND_ROW_HEIGHT_POINTS);
        }
        Cell name = sheet.getRow(r).createCell(0);
        name.setCellValue(COMPANY_NAME);
        name.setCellStyle(ExcelStyles.companyName(workbook));
        sheet.addMergedRegion(new CellRangeAddress(r, r + BAND_ROWS - 1, 0, columnCount - 1));
        placeLogo(sheet, r, columnCount);
        r += BAND_ROWS;

        CellStyle titleStyle = ExcelStyles.title(workbook);
        for (String line : concat(title, lines)) {
            if (line == null || line.isBlank()) continue;
            Row row = sheet.createRow(r);
            Cell cell = row.createCell(0);
            cell.setCellValue(line);
            cell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(r, r, 0, columnCount - 1));
            r++;
        }
        return r;
    }

    /**
     * Floats the logo over the top corner of the band. The anchor's far corner is walked
     * out across the real column widths and row heights, so the logo keeps its proportions
     * no matter how wide the sheet's columns happen to be.
     */
    private void placeLogo(Sheet sheet, int bandStartRow, int columnCount) {
        byte[] image = logo();
        if (image == null) return;

        Workbook workbook = sheet.getWorkbook();
        int pictureIndex = workbook.addPicture(image, Workbook.PICTURE_TYPE_PNG);
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
        anchor.setCol1(0);
        anchor.setRow1(bandStartRow);
        anchor.setDx1(Units.pixelToEMU(LOGO_MARGIN_PX));
        anchor.setDy1(Units.pixelToEMU(LOGO_MARGIN_PX));

        int width = (int) Math.round(LOGO_HEIGHT_PX * LOGO_ASPECT) + LOGO_MARGIN_PX;
        int col = 0;
        while (col < columnCount - 1) {
            int columnWidth = Math.max(1, (int) sheet.getColumnWidthInPixels(col));
            if (width <= columnWidth) break;
            width -= columnWidth;
            col++;
        }
        anchor.setCol2(col);
        anchor.setDx2(Units.pixelToEMU(width));

        int rowHeight = (int) Math.round(BAND_ROW_HEIGHT_POINTS * PIXELS_PER_POINT);
        int height = LOGO_HEIGHT_PX + LOGO_MARGIN_PX;
        int rowsDown = Math.min(height / rowHeight, BAND_ROWS - 1);
        anchor.setRow2(bandStartRow + rowsDown);
        anchor.setDy2(Units.pixelToEMU(height - rowsDown * rowHeight));

        drawing.createPicture(anchor, pictureIndex);
    }

    private byte[] logo() {
        if (!logoLoaded) {
            synchronized (this) {
                if (!logoLoaded) {
                    try (InputStream in = new ClassPathResource(LOGO_RESOURCE).getInputStream()) {
                        logo = in.readAllBytes();
                    } catch (IOException | RuntimeException e) {
                        log.warn("Company logo {} could not be read; exporting without it: {}",
                                LOGO_RESOURCE, e.getMessage());
                    }
                    logoLoaded = true;
                }
            }
        }
        return logo;
    }

    private static String[] concat(String first, String[] rest) {
        String[] all = new String[rest.length + 1];
        all[0] = first;
        System.arraycopy(rest, 0, all, 1, rest.length);
        return all;
    }
}
