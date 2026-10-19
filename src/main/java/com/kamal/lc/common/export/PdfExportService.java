package com.kamal.lc.common.export;

import com.kamal.lc.common.export.ExcelBranding;
import com.kamal.lc.payroll.dto.AdjustmentResponse;
import com.kamal.lc.payroll.dto.PayrollLineResponse;
import com.kamal.lc.payroll.dto.PayrollRunResponse;
import com.kamal.lc.payroll.dto.StaffPayrollLineResponse;
import com.kamal.lc.settings.service.SettingsResolver;
import com.kamal.lc.shift.dto.ShiftFilter;
import com.kamal.lc.shift.dto.ShiftResponse;
import com.kamal.lc.shift.entity.Shift;
import com.kamal.lc.shift.repository.ShiftRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;

/**
 * Service to generate PDF documents using OpenPDF (LGPL).
 * Supports:
 * - Payroll Runs (Captains & Staff) PDF
 * - Shifts Report PDF
 * - Individual Payslip PDF (Single A4 format matching sample OPR)
 * - Bulk Payslips PDF
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final ShiftRepository shiftRepository;
    private final SettingsResolver settingsResolver;

    private BaseFont baseFont;

    private BaseFont getArabicBaseFont() {
        if (baseFont == null) {
            try {
                InputStream is = new ClassPathResource("fonts/Cairo-Regular.ttf").getInputStream();
                byte[] fontBytes = is.readAllBytes();
                baseFont = BaseFont.createFont("Cairo-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
            } catch (Exception e) {
                log.warn("Could not load Cairo font from classpath: {}", e.getMessage());
            }

            if (baseFont == null) {
                String[] fontPaths = {
                    "C:\\Windows\\Fonts\\arial.ttf",
                    "C:\\Windows\\Fonts\\tahoma.ttf",
                    "C:\\Windows\\Fonts\\seguiemj.ttf",
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
                };
                for (String path : fontPaths) {
                    if (new File(path).exists()) {
                        try {
                            baseFont = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                            break;
                        } catch (Exception ex) {
                            log.warn("Failed to load fallback font from {}: {}", path, ex.getMessage());
                        }
                    }
                }
            }

            if (baseFont == null) {
                try {
                    baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                } catch (Exception e) {
                    log.error("Failed to create fallback font", e);
                }
            }
        }
        return baseFont;
    }

    public Font getFont(float size, int style, Color color) {
        BaseFont bf = getArabicBaseFont();
        return new Font(bf, size, style, color);
    }

    public Font getFont(float size, int style) {
        return getFont(size, style, Color.BLACK);
    }

    public Font getFont(float size) {
        return getFont(size, Font.NORMAL, Color.BLACK);
    }

    /**
     * Shapes Arabic text using ICU4J for correct rendering in PDFs.
     * Uses ArabicShaping to join letters into their contextual forms
     * (initial, medial, final, isolated).
     *
     * Note: BiDi reordering is NOT done here — it is handled by
     * PdfPCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL) at the cell level.
     */
    private String shapeArabic(String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            ArabicShaping shaping = new ArabicShaping(ArabicShaping.LETTERS_SHAPE);
            return shaping.shape(text);
        } catch (ArabicShapingException e) {
            log.warn("Arabic shaping failed for text: {}", text, e);
            return text;
        }
    }

    /**
     * Adds a paragraph of text to the document with proper RTL support.
     * Uses ICU4J shapeArabic() for letter shaping + cell RTL direction for BiDi.
     */
    private void addRtlParagraph(Document doc, String text, Font font, int alignment) throws DocumentException {
        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(text), font));
        cell.setHorizontalAlignment(alignment);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        wrapper.addCell(cell);
        doc.add(wrapper);
    }

    private byte[] loadLogoBytes() {
        try (InputStream is = new ClassPathResource("branding/logo-ksclc.png").getInputStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.warn("Could not load logo image: {}", e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // 1. Captains Payroll PDF Report — Classic white organisational layout
    // =========================================================================
    public byte[] buildPayrollPdf(PayrollRunResponse run, List<PayrollLineResponse> lines) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 15, 15, 15, 15);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // ---- Classic White Masthead ----
            addClassicMasthead(document, run);

            // ---- Data Table ----
            String[] headers = {
                "ID-KSLSC\ncaptain_id", "اسم الكابتن\ncaptain name",
                "عدد الشفتات\nTOTAL SHIFT", "اجمالي الطلبات\nTotal Delivered",
                "اجمالي الساعات\nTotal Working\nHour (Total)", "تحقيق التارقيت\nACHIEVE THE\nTARGET",
                "خصم تذاكر\nDeduction for\nlate", "عمولة بعد\nالتارقت 18 طلب\nAfter TARGET",
                "SUM of الطلبات\nTOTAL SALARY\nORDER", "بدل سيارة\nCAR\nALLOWANCE",
                "تحقيق الساعات\nTOTAL TARGET\nHOUR", "مجموع الراتب\nTOTAL SALARY",
                "بدل سكن\nHousing\nallowance", "مكافأة\nREWARD",
                "فروقات البنزين\nGasoline price\ndifferences", "غرامة نينجا\nNinja fine or\nviolation",
                "سلف\nLOAN", "صافي الراتب\nNET SALARY"
            };
            float[] widths = { 5, 11, 5, 5, 6, 6, 6, 7, 6, 5, 6, 6, 5, 5, 6, 6, 5, 7 };

            PdfPTable table = new PdfPTable(widths.length);
            table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            table.setWidthPercentage(100);
            table.setWidths(widths);

            addClassicHeaderRow(table, headers);

            BigDecimal sumTarget = BigDecimal.ZERO, sumLate = BigDecimal.ZERO, sumExtra = BigDecimal.ZERO;
            BigDecimal sumBase = BigDecimal.ZERO, sumCar = BigDecimal.ZERO, sumGross = BigDecimal.ZERO;
            BigDecimal sumNet = BigDecimal.ZERO;
            int totalOrders = 0, totalShifts = 0;

            for (PayrollLineResponse line : lines) {
                addClassicCell(table, line.getCaptainCode());
                addClassicCell(table, line.getCaptainName());
                addClassicCell(table, String.valueOf(line.getAttendedShifts()));
                addClassicCell(table, String.valueOf(line.getTotalOrders()));
                addClassicCell(table, fmt(line.getTotalHours()));
                addClassicCell(table, fmtMoney(line.getTargetBonus()));
                addClassicCell(table, fmtMoney(line.getLateDeductions()));
                addClassicCell(table, fmtMoney(line.getExtraOrderPay()));
                addClassicCell(table, fmtMoney(line.getBasePay()));
                addClassicCell(table, fmtMoney(line.getCarAllowances()));
                addClassicCell(table, fmt(line.getTotalHours()));
                addClassicCell(table, fmtMoney(line.getGross()));
                addClassicCell(table, fmtMoney(adjAmount(line, "HOUSING_ALLOWANCE")));
                addClassicCell(table, fmtMoney(adjAmount(line, "REWARD")));
                addClassicCell(table, fmtMoney(adjAmount(line, "FUEL_DIFFERENCE")));
                addClassicCell(table, fmtMoney(line.getSupplierDeductions()));
                addClassicCell(table, fmtMoney(adjAmount(line, "LOAN")));
                addClassicCell(table, fmtMoney(line.getNet()));

                totalShifts += line.getAttendedShifts();
                totalOrders += line.getTotalOrders();
                sumTarget = sumTarget.add(nz(line.getTargetBonus()));
                sumLate = sumLate.add(nz(line.getLateDeductions()));
                sumExtra = sumExtra.add(nz(line.getExtraOrderPay()));
                sumBase = sumBase.add(nz(line.getBasePay()));
                sumCar = sumCar.add(nz(line.getCarAllowances()));
                sumGross = sumGross.add(nz(line.getGross()));
                sumNet = sumNet.add(nz(line.getNet()));
            }

            // Total row
            addClassicTotalCell(table, "المجموع / TOTAL", 2);
            addClassicTotalCell(table, String.valueOf(totalShifts), 1);
            addClassicTotalCell(table, String.valueOf(totalOrders), 1);
            addClassicTotalCell(table, "—", 1);
            addClassicTotalCell(table, fmtMoney(sumTarget), 1);
            addClassicTotalCell(table, fmtMoney(sumLate), 1);
            addClassicTotalCell(table, fmtMoney(sumExtra), 1);
            addClassicTotalCell(table, fmtMoney(sumBase), 1);
            addClassicTotalCell(table, fmtMoney(sumCar), 1);
            addClassicTotalCell(table, "—", 1);
            addClassicTotalCell(table, fmtMoney(sumGross), 1);
            addClassicTotalCell(table, "—", 1);
            addClassicTotalCell(table, "—", 1);
            addClassicTotalCell(table, "—", 1);
            addClassicTotalCell(table, "—", 1);
            addClassicTotalCell(table, "—", 1);
            addClassicTotalCell(table, fmtMoney(sumNet), 1);

            document.add(table);
            addOfficialSignatures(document);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build captain payroll PDF", e);
            throw new RuntimeException("Error generating PDF report", e);
        }
    }

    // =========================================================================
    // 2. Staff Payroll PDF Report — Classic white organisational layout
    // =========================================================================
    public byte[] buildStaffPayrollPdf(PayrollRunResponse run, List<StaffPayrollLineResponse> lines, String staffType) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 15, 15, 15, 15);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // ---- Classic White Masthead (reuse same style) ----
            String typeAr = "WORKER".equalsIgnoreCase(staffType) ? "العاملين"
                    : "MANAGER".equalsIgnoreCase(staffType) ? "الإداريين" : "الموظفين";

            addClassicStaffMasthead(document, run, typeAr);

            String[] headers = {
                "الكود\nCode", "اسم الموظف\nName", "المسمى الوظيفي\nJob Title",
                "الراتب الأساسي\nBasic", "بدل السكن\nHousing", "بدلات أخرى\nOther All.",
                "الإجمالي\nGross", "الخصومات/الإضافات\nAdjustments", "صافي الراتب\nNet Pay"
            };
            float[] widths = { 10, 25, 18, 12, 12, 12, 12, 14, 15 };

            PdfPTable table = new PdfPTable(widths.length);
            table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            table.setWidthPercentage(100);
            table.setWidths(widths);

            addClassicHeaderRow(table, headers);

            BigDecimal sumBasic = BigDecimal.ZERO, sumHousing = BigDecimal.ZERO, sumOther = BigDecimal.ZERO;
            BigDecimal sumGross = BigDecimal.ZERO, sumAdj = BigDecimal.ZERO, sumNet = BigDecimal.ZERO;

            for (StaffPayrollLineResponse line : lines) {
                addClassicCell(table, line.getStaffCode());
                addClassicCell(table, line.getStaffName());
                addClassicCell(table, line.getJobTitle() != null ? line.getJobTitle() : "—");
                addClassicCell(table, fmtMoney(line.getBasicSalary()));
                addClassicCell(table, fmtMoney(line.getHousingAllowance()));
                addClassicCell(table, fmtMoney(line.getOtherAllowances()));
                addClassicCell(table, fmtMoney(line.getGross()));
                addClassicCell(table, fmtMoney(line.getAdjustmentsTotal()));
                addClassicCell(table, fmtMoney(line.getNet()));

                sumBasic = sumBasic.add(nz(line.getBasicSalary()));
                sumHousing = sumHousing.add(nz(line.getHousingAllowance()));
                sumOther = sumOther.add(nz(line.getOtherAllowances()));
                sumGross = sumGross.add(nz(line.getGross()));
                sumAdj = sumAdj.add(nz(line.getAdjustmentsTotal()));
                sumNet = sumNet.add(nz(line.getNet()));
            }

            addClassicTotalCell(table, "المجموع / TOTAL", 3);
            addClassicTotalCell(table, fmtMoney(sumBasic), 1);
            addClassicTotalCell(table, fmtMoney(sumHousing), 1);
            addClassicTotalCell(table, fmtMoney(sumOther), 1);
            addClassicTotalCell(table, fmtMoney(sumGross), 1);
            addClassicTotalCell(table, fmtMoney(sumAdj), 1);
            addClassicTotalCell(table, fmtMoney(sumNet), 1);

            document.add(table);
            addOfficialSignatures(document);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build staff payroll PDF", e);
            throw new RuntimeException("Error generating staff PDF report", e);
        }
    }

    // =========================================================================
    // 3. Shifts PDF Report
    // =========================================================================
    public byte[] buildShiftsPdf(List<ShiftResponse> shifts, ShiftFilter filter) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 15, 15, 15, 15);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            String periodStr = (filter.getFrom() == null && filter.getTo() == null) ? "كل الفترات"
                    : "من " + filter.getFrom() + " إلى " + filter.getTo();

            addHeaderTable(document, "تقرير الشيفتات / SHIFTS REPORT",
                    "الفترة: " + periodStr + "   |   عدد الشيفتات: " + shifts.size());

            String[] headers = {
                "التاريخ\nDate", "ID-Ninja", "اسم الكابتن\nCaptain Name", "المنطقة\nZone",
                "الحالة\nStatus", "ساعات\nHours", "طلبات\nOrders", "اليومية\nWage",
                "خصم تأخير\nLate Dec.", "حافز\nInc.", "بنزين\nFuel", "صافي الشفت\nNet Pay", "خصم المورد\nSupp. Dec."
            };
            float[] widths = { 10, 10, 22, 12, 8, 8, 8, 10, 10, 10, 10, 12, 10 };

            PdfPTable table = createRtlTable(widths);
            addHeaderRow(table, headers);

            for (ShiftResponse s : shifts) {
                String captainDisp = s.getCaptainName() != null ? s.getCaptainName() : "غير مربوط";
                String statusStr = s.isExcluded() ? "مستبعد" : Boolean.TRUE.equals(s.getAttended()) ? "حضور" : "غياب";

                addCell(table, s.getShiftDate(), false);
                addCell(table, s.getNinjaCaptainId(), false);
                addCell(table, captainDisp, false);
                addCell(table, s.getZoneName() != null ? s.getZoneName() : "—", false);
                addCell(table, statusStr, false);
                addCell(table, fmt(s.getWorkingHours()), true);
                addCell(table, String.valueOf(s.getTotalDelivered() != null ? s.getTotalDelivered() : 0), true);
                addCell(table, fmtMoney(s.getDailyWage()), true);
                addCell(table, fmtMoney(s.getLateDeduction()), true);
                addCell(table, fmtMoney(s.getDailyIncentive()), true);
                addCell(table, fmtMoney(s.getFuelAllowance()), true);
                addCell(table, fmtMoney(s.getNetShiftPay()), true, true);
                addCell(table, fmtMoney(s.getSupplierDeduction()), true);
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build shifts PDF", e);
            throw new RuntimeException("Error generating shifts PDF", e);
        }
    }

    // =========================================================================
    // 4. Individual Payslip PDF (A4 Page matching sample format)
    // =========================================================================
    public byte[] buildPayslipPdf(PayrollRunResponse run, PayrollLineResponse line) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 25, 25, 25, 25);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            renderSinglePayslip(document, run, line);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build payslip PDF", e);
            throw new RuntimeException("Error generating payslip PDF", e);
        }
    }

    /** Bulk export: generates a single PDF file with one A4 payslip per member */
    public byte[] buildBulkPayslipsPdf(PayrollRunResponse run, List<PayrollLineResponse> lines) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 25, 25, 25, 25);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    document.newPage();
                }
                renderSinglePayslip(document, run, lines.get(i));
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build bulk payslips PDF", e);
            throw new RuntimeException("Error generating bulk payslips PDF", e);
        }
    }

    private void renderSinglePayslip(Document document, PayrollRunResponse run, PayrollLineResponse line) throws DocumentException {
        // Colors matching white theme for clean printing
        Color headerBg = Color.WHITE;
        Color borderColor = new Color(200, 200, 200);
        Color goldColor = new Color(218, 165, 32);
        Color darkText = new Color(30, 30, 30);

        Font fontTitleBig = getFont(14, Font.BOLD, darkText);
        Font fontTitle = getFont(10, Font.BOLD, darkText);
        Font fontNormal = getFont(8, Font.NORMAL, darkText);
        Font fontBold = getFont(8, Font.BOLD, darkText);
        Font fontSmall = getFont(7, Font.NORMAL, Color.DARK_GRAY);
        Font fontBoldSmall = getFont(7, Font.BOLD, darkText);

        // Derive period info
        LocalDate periodFrom = LocalDate.parse(run.getPeriodFrom());
        LocalDate periodTo = LocalDate.parse(run.getPeriodTo());
        String monthNameEn = periodFrom.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
        String monthNameAr = arabicMonthName(periodFrom.getMonthValue());
        int monthNum = periodFrom.getMonthValue();
        int year = periodFrom.getYear();
        String periodLabel = "شهر " + monthNameAr + " " + year;
        String periodLabelEn = monthNameEn.toUpperCase() + " " + year + " Payroll Slip";

        // Get target from settings
        int target;
        try {
            target = settingsResolver.resolve(periodFrom).getMonthlyOrderTarget();
        } catch (Exception e) {
            target = 350; // fallback
        }

        // ==================== SECTION 1: HEADER ====================
        PdfPTable headerOuter = new PdfPTable(3);
        headerOuter.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        headerOuter.setWidthPercentage(100);
        headerOuter.setWidths(new float[]{25, 40, 35});

        // Right cell: Logo (centered graphic only)
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.BOX);
        logoCell.setBorderColor(borderColor);
        logoCell.setPadding(6);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        byte[] logoBytes = loadLogoBytes();
        if (logoBytes != null) {
            try {
                Image img = Image.getInstance(logoBytes);
                img.setAlignment(Element.ALIGN_CENTER);
                img.scaleToFit(100, 70);
                logoCell.addElement(img);
            } catch (Exception e) {
                log.warn("Failed to add logo", e);
            }
        }
        headerOuter.addCell(logoCell);

        // Center cell: Title (text pre-shaped via ICU4J)
        Phrase titlePhrase = new Phrase();
        titlePhrase.add(new Chunk(shapeArabic("مسير راتب " + periodLabel) + "\n", fontTitleBig));
        titlePhrase.add(new Chunk(periodLabelEn, getFont(10, Font.BOLD, darkText)));
        PdfPCell titleCell = new PdfPCell(titlePhrase);
        titleCell.setBorder(Rectangle.BOX);
        titleCell.setBorderColor(borderColor);
        titleCell.setPadding(8);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        headerOuter.addCell(titleCell);

        // Left cell: Metadata table
        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        metaTable.setWidthPercentage(100);
        metaTable.setWidths(new float[]{55, 45});

        String oprNo = "OPR - " + (line.getId() != null ? line.getId().toString().substring(0, 4).toUpperCase() : "0001");
        addMetaRow(metaTable, oprNo, "رقم النموذج\nModel Number", fontNormal, fontBoldSmall, headerBg, borderColor);
        addMetaRow(metaTable, "مسير راتب " + monthNameAr + " " + year,
                "نوع النموذج\nModel Type", fontNormal, fontBoldSmall, headerBg, borderColor);
        String monthStr = String.format("%02d-%d", monthNum, year);
        addMetaRow(metaTable, monthStr, "رواتب شهر\nMONTHLY PAYROLL", fontNormal, fontBoldSmall, headerBg, borderColor);
        addMetaRow(metaTable, periodTo.getDayOfMonth() + "/" + periodTo.getMonthValue() + "/" + periodTo.getYear(),
                "تاريخ اليوم\nDate", fontNormal, fontBoldSmall, headerBg, borderColor);

        PdfPCell metaCell = new PdfPCell(metaTable);
        metaCell.setBorder(Rectangle.BOX);
        metaCell.setBorderColor(borderColor);
        metaCell.setPadding(0);
        headerOuter.addCell(metaCell);

        document.add(headerOuter);
        document.add(spacer(6));

        // ==================== SECTION 2: MEMBER INFO ====================
        PdfPTable memberTable = new PdfPTable(5);
        memberTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        memberTable.setWidthPercentage(100);
        memberTable.setWidths(new float[]{12, 28, 12, 23, 25});

        // Row 1: Arabic name + Code
        addBorderedCell(memberTable, "NAME :", fontBold, borderColor, Element.ALIGN_CENTER, headerBg);

        String nameAr = line.getCaptainName() != null ? line.getCaptainName() : "—";
        addBorderedCell(memberTable, nameAr, fontBold, borderColor, Element.ALIGN_CENTER, Color.WHITE);

        addBorderedCell(memberTable, ": الاسم", fontBold, borderColor, Element.ALIGN_CENTER, headerBg);

        addBorderedCell(memberTable, line.getCaptainCode() != null ? line.getCaptainCode() : "—",
                fontBold, borderColor, Element.ALIGN_CENTER, Color.WHITE);

        PdfPCell jobIdLabelCell = new PdfPCell(new Phrase(shapeArabic("الرقم الوظيفي") + "\njob ID", fontBoldSmall));
        jobIdLabelCell.setBorder(Rectangle.BOX);
        jobIdLabelCell.setBorderColor(borderColor);
        jobIdLabelCell.setBackgroundColor(headerBg);
        jobIdLabelCell.setPadding(4);
        jobIdLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        jobIdLabelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        memberTable.addCell(jobIdLabelCell);

        // Row 2: English name + Ninja ID
        addBorderedCell(memberTable, "", fontNormal, borderColor, Element.ALIGN_CENTER, Color.WHITE);

        String nameEn = line.getCaptainNameEn() != null ? line.getCaptainNameEn() : "";
        addBorderedCell(memberTable, nameEn, fontBold, borderColor, Element.ALIGN_CENTER, Color.WHITE);

        addBorderedCell(memberTable, "", fontNormal, borderColor, Element.ALIGN_CENTER, Color.WHITE);

        String ninjaId = line.getSupplierExternalIds() != null ? line.getSupplierExternalIds() : "—";
        addBorderedCell(memberTable, ninjaId, fontBold, borderColor, Element.ALIGN_CENTER, Color.WHITE);

        PdfPCell ninjaLabelCell = new PdfPCell(new Phrase("ID App NINJA", fontBoldSmall));
        ninjaLabelCell.setBorder(Rectangle.BOX);
        ninjaLabelCell.setBorderColor(borderColor);
        ninjaLabelCell.setBackgroundColor(headerBg);
        ninjaLabelCell.setPadding(4);
        ninjaLabelCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        ninjaLabelCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        memberTable.addCell(ninjaLabelCell);

        document.add(memberTable);
        document.add(spacer(6));

        // ==================== SECTION 3: CAPTAIN PERFORMANCE ====================
        addRtlParagraph(document, "Captain's Performance", fontTitle, Element.ALIGN_CENTER);
        document.add(spacer(4));

        PdfPTable perfOuter = new PdfPTable(2);
        perfOuter.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        perfOuter.setWidthPercentage(100);
        perfOuter.setWidths(new float[]{55, 45});

        // Left side: Bar chart (drawn as colored rectangles)
        PdfPCell chartCell = new PdfPCell();
        chartCell.setBorder(Rectangle.BOX);
        chartCell.setBorderColor(borderColor);
        chartCell.setPadding(6);
        chartCell.setMinimumHeight(130);

        // Draw simple bar chart using nested table
        PdfPTable chartTable = new PdfPTable(5);
        chartTable.setWidthPercentage(100);
        chartTable.setWidths(new float[]{20, 20, 20, 20, 20});

        int totalOrders = line.getTotalOrders();
        BigDecimal totalHrs = line.getTotalHours() != null ? line.getTotalHours() : BigDecimal.ZERO;
        int shifts = line.getAttendedShifts();
        int targetVal = target;

        // Calculate max for scaling bars
        double maxVal = Math.max(Math.max(totalOrders, totalHrs.doubleValue()), Math.max(shifts, targetVal));
        if (maxVal == 0) maxVal = 1;

        // Create bar cells - each bar is a mini table with empty top + colored bottom
        addBarCell(chartTable, totalOrders, maxVal, goldColor, " Orders", fontSmall);
        addBarCell(chartTable, totalHrs.doubleValue(), maxVal, Color.BLACK, " Hours", fontSmall);
        addBarCell(chartTable, shifts, maxVal, Color.DARK_GRAY, " Shifts", fontSmall);
        addBarCell(chartTable, targetVal, maxVal, goldColor, " Target", fontSmall);

        // Empty 5th column for spacing
        PdfPCell emptyBar = new PdfPCell(new Phrase("", fontSmall));
        emptyBar.setBorder(Rectangle.NO_BORDER);
        chartTable.addCell(emptyBar);

        chartCell.addElement(chartTable);

        // Add x-axis label
        // Wrap axis label in RTL table for proper Arabic rendering
        PdfPTable axisWrapper = new PdfPTable(1);
        axisWrapper.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        axisWrapper.setWidthPercentage(100);
        PdfPCell axisLblCell = new PdfPCell(new Phrase(shapeArabic("Captain's Performance"), fontSmall));
        axisLblCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        axisLblCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        axisLblCell.setBorder(Rectangle.NO_BORDER);
        axisWrapper.addCell(axisLblCell);
        chartCell.addElement(axisWrapper);
        perfOuter.addCell(chartCell);

        // Right side: Performance stats table
        PdfPTable statsTable = new PdfPTable(2);
        statsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        statsTable.setWidthPercentage(100);
        statsTable.setWidths(new float[]{40, 60});

        addStatsRow(statsTable, String.valueOf(totalOrders), "عدد الطلبات\nTotal Delivered", fontBold, fontBoldSmall, borderColor);
        BigDecimal displayHours = totalHrs.setScale(2, RoundingMode.HALF_UP);
        addStatsRow(statsTable, displayHours.toString(), "عدد الساعات\nTotal Time", fontBold, fontBoldSmall, borderColor);
        addStatsRow(statsTable, String.valueOf(shifts), "عدد الشفتات\nNUMBER OF SHIFT", fontBold, fontBoldSmall, borderColor);
        addStatsRow(statsTable, String.valueOf(targetVal), "التارقيت\nTarget", fontBold, fontBoldSmall, borderColor);

        PdfPCell statsCell = new PdfPCell(statsTable);
        statsCell.setBorder(Rectangle.BOX);
        statsCell.setBorderColor(borderColor);
        statsCell.setPadding(0);
        perfOuter.addCell(statsCell);

        document.add(perfOuter);
        document.add(spacer(6));

        // ==================== SECTION 4: PAYROLL SLIP ====================
        // Period title
        addRtlParagraph(document, periodLabel, fontTitle, Element.ALIGN_CENTER);
        addRtlParagraph(document, periodLabelEn, fontTitle, Element.ALIGN_CENTER);

        // Salary grid: 4 columns × multiple rows (RTL: rightmost label first)
        PdfPTable salaryTable = new PdfPTable(8);
        salaryTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        salaryTable.setWidthPercentage(100);
        salaryTable.setWidths(new float[]{10, 15, 10, 15, 10, 15, 10, 15});

        // Extract adjustment values
        BigDecimal housingAllowance = adjAmount(line, "HOUSING_ALLOWANCE");
        BigDecimal reward = adjAmount(line, "REWARD");
        BigDecimal fuelDifference = adjAmount(line, "FUEL_DIFFERENCE");
        BigDecimal supplierPenalty = nz(line.getSupplierDeductions());
        BigDecimal loan = adjAmount(line, "LOAN");

        BigDecimal lateDeduction = nz(line.getLateDeductions());
        BigDecimal totalSalary = nz(line.getGross());
        BigDecimal carAllowance = nz(line.getCarAllowances());
        BigDecimal targetBonusVal = nz(line.getTargetBonus());
        BigDecimal extraOrderPay = nz(line.getExtraOrderPay());
        BigDecimal vacationCommission = nz(line.getVacationCommission());

        // Row 1
        addSalaryValueCell(salaryTable, fmtMoney(lateDeduction), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "خصم تأخير\nDeduction for late", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, fmtMoney(totalSalary), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "اجمالي الراتب\nTOTAL SALARY", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, fmtMoney(loan), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "سلف\nLOAN", fontSmall, borderColor);
        // Empty right-side value
        addSalaryValueCell(salaryTable, "", fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "", fontSmall, borderColor);

        // Row 2
        addSalaryValueCell(salaryTable, fmtMoney(supplierPenalty), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "غرامة نينجا\nDEDUCTION NINJA APP", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, fmtMoney(housingAllowance), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "بدل سكن\nHousing allowance", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, fmtMoney(vacationCommission), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "عمولة الإجازة\nVacation Bonus", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, "", fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "", fontSmall, borderColor);

        // Row 3
        addSalaryValueCell(salaryTable, fmtMoney(nz(line.getTotalHours())), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "تحقيق الساعات\nHours Achieved", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, fmtMoney(reward), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "مكافأة\nREWARD", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, "", fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, "", fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "", fontSmall, borderColor);

        // Row 4
        addSalaryValueCell(salaryTable, fmtMoney(targetBonusVal.add(extraOrderPay)), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "تحقيق التارقيت\nTarget Achieved", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, fmtMoney(carAllowance), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "بدل سيارة\nCAR ALLOWANCE", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, fmtMoney(fuelDifference), fontBold, borderColor);
        addSalaryLabelCell(salaryTable, "فروقات البنزين\nGasoline price differences", fontSmall, borderColor);
        addSalaryValueCell(salaryTable, fmtMoney(nz(line.getNet())), getFont(10, Font.BOLD, darkText), borderColor);
        addSalaryLabelCell(salaryTable, "صافي الراتب\nNET SALARY", getFont(8, Font.BOLD, darkText), borderColor);

        document.add(salaryTable);
        document.add(spacer(6));

        // ==================== SECTION 5: NINJA DEDUCTION TABLE ====================
        addRtlParagraph(document, "DEDUCTION NINJA APP", fontTitle, Element.ALIGN_CENTER);
        document.add(spacer(4));

        PdfPTable ninjaTable = new PdfPTable(6);
        ninjaTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        ninjaTable.setWidthPercentage(100);
        ninjaTable.setWidths(new float[]{10, 20, 15, 15, 25, 15});

        // Header row
        String[] ninjaHeaders = {
                "كود الكابتن\nID Captain",
                "رقم الحالة\nCase Number",
                "تاريخ الحالة\nDate Status",
                "رقم الطلب\nOrder No.",
                "الملاحظة , سبب المخالفة او الغرامة\nNOTE , Reason for violation or fine",
                "المجموع\nTotal"
        };
        for (String h : ninjaHeaders) {
            PdfPCell hc = new PdfPCell(new Phrase(shapeArabic(h), fontBoldSmall));
            hc.setBackgroundColor(headerBg);
            hc.setBorder(Rectangle.BOX);
            hc.setBorderColor(borderColor);
            hc.setHorizontalAlignment(Element.ALIGN_CENTER);
            hc.setVerticalAlignment(Element.ALIGN_MIDDLE);
            hc.setPadding(4);
            hc.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            ninjaTable.addCell(hc);
        }

        // Get supplier deduction data from shifts
        BigDecimal ninjaGrandTotal = BigDecimal.ZERO;
        try {
            List<Shift> deductionShifts = shiftRepository.findSupplierDeductionsByCaptain(
                    line.getCaptainId(), periodFrom, periodTo);

            for (Shift s : deductionShifts) {
                BigDecimal deductionAmt = nz(s.getSupplierDeduction());
                ninjaGrandTotal = ninjaGrandTotal.add(deductionAmt);

                // Captain code
                addBorderedCell(ninjaTable, line.getCaptainCode(), fontNormal, borderColor, Element.ALIGN_CENTER, Color.WHITE);
                // Case Number — placeholder
                addBorderedCell(ninjaTable, "—", fontNormal, borderColor, Element.ALIGN_CENTER, Color.WHITE);
                // Date Status
                String dateStr = s.getShiftDate() != null ?
                        s.getShiftDate().format(DateTimeFormatter.ofPattern("M/d/yyyy")) : "—";
                addBorderedCell(ninjaTable, dateStr, fontNormal, borderColor, Element.ALIGN_CENTER, Color.WHITE);
                // Order No. — placeholder
                addBorderedCell(ninjaTable, "—", fontNormal, borderColor, Element.ALIGN_CENTER, Color.WHITE);
                // Note / Reason for violation or fine (from imported supplier deduction)
                String reasonStr = s.getSupplierDeductionReason() != null && !s.getSupplierDeductionReason().isBlank()
                        ? s.getSupplierDeductionReason()
                        : "—";
                addBorderedCell(ninjaTable, reasonStr, fontNormal, borderColor, Element.ALIGN_CENTER, Color.WHITE);
                // Total
                addBorderedCell(ninjaTable, fmtMoney(deductionAmt), fontBold, borderColor, Element.ALIGN_CENTER, Color.WHITE);
            }

            if (deductionShifts.isEmpty()) {
                // Add empty row
                for (int i = 0; i < 6; i++) {
                    addBorderedCell(ninjaTable, "—", fontNormal, borderColor, Element.ALIGN_CENTER, Color.WHITE);
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch supplier deductions for payslip: {}", e.getMessage());
            for (int i = 0; i < 6; i++) {
                addBorderedCell(ninjaTable, "—", fontNormal, borderColor, Element.ALIGN_CENTER, Color.WHITE);
            }
        }

        document.add(ninjaTable);
        document.add(spacer(4));

        // ==================== SECTION 6: GRAND TOTAL ====================
        PdfPTable grandTotalTable = new PdfPTable(2);
        grandTotalTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        grandTotalTable.setWidthPercentage(100);
        grandTotalTable.setWidths(new float[]{20, 80});

        PdfPCell gtValueCell = new PdfPCell(new Phrase(fmtMoney(ninjaGrandTotal), fontBold));
        gtValueCell.setBorder(Rectangle.BOX);
        gtValueCell.setBorderColor(borderColor);
        gtValueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        gtValueCell.setPadding(5);
        grandTotalTable.addCell(gtValueCell);

        PdfPCell gtLabelCell = new PdfPCell(new Phrase("Grand Total", fontBold));
        gtLabelCell.setBorder(Rectangle.BOX);
        gtLabelCell.setBorderColor(borderColor);
        gtLabelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        gtLabelCell.setPadding(5);
        grandTotalTable.addCell(gtLabelCell);

        document.add(grandTotalTable);

        // ==================== SECTION 7: SIGNATURES ====================
        document.add(spacer(10));

        PdfPTable signTable = new PdfPTable(2);
        signTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signTable.setWidthPercentage(100);
        signTable.setWidths(new float[]{50, 50});

        PdfPCell s1 = new PdfPCell(new Phrase(shapeArabic("توقيع المحاسب / Accountant Signature:") + "\n\n_______________________", getFont(9, Font.NORMAL, Color.DARK_GRAY)));
        s1.setBorder(Rectangle.NO_BORDER);
        s1.setPadding(6);
        s1.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signTable.addCell(s1);

        PdfPCell s2 = new PdfPCell(new Phrase(shapeArabic("توقيع المستلم / Receiver Signature:") + "\n\n_______________________", getFont(9, Font.NORMAL, Color.DARK_GRAY)));
        s2.setBorder(Rectangle.NO_BORDER);
        s2.setHorizontalAlignment(Element.ALIGN_LEFT);
        s2.setPadding(6);
        s2.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signTable.addCell(s2);

        document.add(signTable);
    }

    // =========================================================================
    // 5. Staff Individual Payslip PDF (Workers & Managers)
    // =========================================================================

    /** Single staff payslip PDF */
    public byte[] buildStaffPayslipPdf(PayrollRunResponse run, StaffPayrollLineResponse line) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 25, 25, 25, 25);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            renderSingleStaffPayslip(document, run, line);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build staff payslip PDF", e);
            throw new RuntimeException("Error generating staff payslip PDF", e);
        }
    }

    /** Bulk staff payslips: one A4 page per employee */
    public byte[] buildBulkStaffPayslipsPdf(PayrollRunResponse run, List<StaffPayrollLineResponse> lines) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 25, 25, 25, 25);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            for (int i = 0; i < lines.size(); i++) {
                if (i > 0) {
                    document.newPage();
                }
                renderSingleStaffPayslip(document, run, lines.get(i));
            }

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build bulk staff payslips PDF", e);
            throw new RuntimeException("Error generating bulk staff payslips PDF", e);
        }
    }

    /**
     * Renders a single staff payslip matching the Excel sample format:
     * Header (logo + title + meta) → Employee ID → Financial grid → Signature
     */
    private void renderSingleStaffPayslip(Document document, PayrollRunResponse run,
                                           StaffPayrollLineResponse line) throws DocumentException {
        // Colors
        Color headerBg = new Color(245, 245, 245);
        Color borderColor = new Color(200, 200, 200);
        Color darkText = new Color(30, 30, 30);
        Color netSalaryBg = new Color(230, 230, 230);

        Font fontTitleBig = getFont(14, Font.BOLD, darkText);
        Font fontTitle = getFont(10, Font.BOLD, darkText);
        Font fontNormal = getFont(9, Font.NORMAL, darkText);
        Font fontBold = getFont(9, Font.BOLD, darkText);
        Font fontSmall = getFont(7, Font.NORMAL, Color.DARK_GRAY);
        Font fontBoldSmall = getFont(7, Font.BOLD, darkText);
        Font fontNetSalary = getFont(11, Font.BOLD, darkText);

        // Derive period info
        LocalDate periodFrom = LocalDate.parse(run.getPeriodFrom());
        LocalDate periodTo = LocalDate.parse(run.getPeriodTo());
        String monthNameAr = arabicMonthName(periodFrom.getMonthValue());
        int monthNum = periodFrom.getMonthValue();
        int year = periodFrom.getYear();

        // ==================== SECTION 1: HEADER ====================
        // 3 columns: Logo (right) | Title (center) | Metadata (left)
        PdfPTable headerOuter = new PdfPTable(3);
        headerOuter.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        headerOuter.setWidthPercentage(100);
        headerOuter.setWidths(new float[]{25, 40, 35});

        // Right cell: Logo (centered graphic only)
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.BOX);
        logoCell.setBorderColor(borderColor);
        logoCell.setPadding(8);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        byte[] logoBytes = loadLogoBytes();
        if (logoBytes != null) {
            try {
                Image img = Image.getInstance(logoBytes);
                img.setAlignment(Element.ALIGN_CENTER);
                img.scaleToFit(90, 60);
                logoCell.addElement(img);
            } catch (Exception e) {
                log.warn("Failed to add logo to staff payslip", e);
            }
        }
        headerOuter.addCell(logoCell);

        // Center cell: Title "مسيرات الرواتب شهر يونيو 6 - 2026"
        Phrase staffTitlePhrase = new Phrase();
        staffTitlePhrase.add(new Chunk(shapeArabic("مسيرات الرواتب") + "\n", fontTitleBig));
        staffTitlePhrase.add(new Chunk(shapeArabic("شهر " + monthNameAr + " " + monthNum + " - " + year), fontTitleBig));
        PdfPCell titleCell = new PdfPCell(staffTitlePhrase);
        titleCell.setBorder(Rectangle.BOX);
        titleCell.setBorderColor(borderColor);
        titleCell.setPadding(10);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        headerOuter.addCell(titleCell);

        // Left cell: Metadata table (Model Number, Model Type, Date)
        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        metaTable.setWidthPercentage(100);
        metaTable.setWidths(new float[]{55, 45});

        addMetaRow(metaTable, "KS - HR - 0001", "رقم النموذج\nModel Number", fontNormal, fontBoldSmall, headerBg, borderColor);
        addMetaRow(metaTable, "مسيرات الرواتب\nPersonal Data", "نوع النموذج\nModel Type", fontNormal, fontBoldSmall, headerBg, borderColor);
        addMetaRow(metaTable, periodTo.getYear() + "/" + String.format("%02d", periodTo.getMonthValue()) + "/" + String.format("%02d", periodTo.getDayOfMonth()),
                "التاريخ\nDate", fontNormal, fontBoldSmall, headerBg, borderColor);

        PdfPCell metaCell = new PdfPCell(metaTable);
        metaCell.setBorder(Rectangle.BOX);
        metaCell.setBorderColor(borderColor);
        metaCell.setPadding(0);
        headerOuter.addCell(metaCell);

        document.add(headerOuter);
        document.add(spacer(10));

        // ==================== SECTION 2: EMPLOYEE ID ====================
        PdfPTable idTable = new PdfPTable(1);
        idTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        idTable.setWidthPercentage(100);

        PdfPCell codeCell = new PdfPCell(new Phrase("الرقم الوظيفي: " + (line.getStaffCode() != null ? line.getStaffCode() : "—"), fontTitle));
        codeCell.setBorder(Rectangle.BOX);
        codeCell.setBorderColor(borderColor);
        codeCell.setPadding(6);
        codeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        codeCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        codeCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        idTable.addCell(codeCell);

        document.add(idTable);
        document.add(spacer(8));

        // ==================== SECTION 3: FINANCIAL DATA GRID ====================
        // Calculate values
        BigDecimal basicSalary = nz(line.getBasicSalary());
        BigDecimal housingAllowance = nz(line.getHousingAllowance());
        BigDecimal otherAllowances = nz(line.getOtherAllowances());
        BigDecimal gross = nz(line.getGross());
        BigDecimal netSalary = nz(line.getNet());

        // Loan amount (separate)
        BigDecimal loanAmount = staffAdjAmount(line, "LOAN");

        // Other deductions (grouped: SUPPLIER_PENALTY + OTHER_DEDUCTION or gross - net - loan)
        BigDecimal explicitDeductions = staffAdjAmount(line, "SUPPLIER_PENALTY")
                .add(staffAdjAmount(line, "OTHER_DEDUCTION"));
        BigDecimal calculatedDeductions = gross.subtract(netSalary).subtract(loanAmount).max(BigDecimal.ZERO);
        BigDecimal deductions = explicitDeductions.compareTo(BigDecimal.ZERO) > 0 ? explicitDeductions : calculatedDeductions;

        // 4 columns: Value (left) | Label (left) | Value (right) | Label (right)
        PdfPTable finTable = new PdfPTable(4);
        finTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        finTable.setWidthPercentage(100);
        finTable.setWidths(new float[]{20, 30, 20, 30});

        // Row 1: الاسم NAME | اجمالي الراتب Total salary
        addFinLabelCell(finTable, "الاسـم\nNAME", fontBoldSmall, borderColor, headerBg);
        addFinValueCell(finTable, line.getStaffName() != null ? line.getStaffName() : "—", fontBold, borderColor, Color.WHITE);
        addFinLabelCell(finTable, "اجمالي الراتب\nTotal salary", fontBoldSmall, borderColor, headerBg);
        addFinValueCell(finTable, fmtMoney(gross), fontBold, borderColor, Color.WHITE);

        // Row 2: الراتب الأساسي Base Salary | سلف loan
        addFinLabelCell(finTable, "الراتب الأساسي\nBase Salary", fontBoldSmall, borderColor, headerBg);
        addFinValueCell(finTable, fmtMoney(basicSalary), fontBold, borderColor, Color.WHITE);
        addFinLabelCell(finTable, "سلف\nloan", fontBoldSmall, borderColor, headerBg);
        addFinValueCell(finTable, fmtMoney(loanAmount), fontBold, borderColor, Color.WHITE);

        // Row 3: بدل سكن Housing Allowance | استقطاعات Deductions
        addFinLabelCell(finTable, "بدل سكن\nHousing Allowance", fontBoldSmall, borderColor, headerBg);
        addFinValueCell(finTable, fmtMoney(housingAllowance), fontBold, borderColor, Color.WHITE);
        addFinLabelCell(finTable, "استقطاعات\nDeductions", fontBoldSmall, borderColor, headerBg);
        addFinValueCell(finTable, fmtMoney(deductions), fontBold, borderColor, Color.WHITE);

        // Row 4: بدل أخرى Other Allowances | صافي الراتب Net Salary
        addFinLabelCell(finTable, "بدل أخرى\nOther Allowances", fontBoldSmall, borderColor, headerBg);
        addFinValueCell(finTable, fmtMoney(otherAllowances), fontBold, borderColor, Color.WHITE);
        addFinLabelCell(finTable, "الصافي الراتب\nNet Salary", fontBoldSmall, borderColor, netSalaryBg);
        addFinValueCell(finTable, fmtMoney(netSalary), fontNetSalary, borderColor, netSalaryBg);

        document.add(finTable);
        document.add(spacer(30));

        // ==================== SECTION 4: SIGNATURE ====================
        addRtlParagraph(document, "توقيع الاستلام :", getFont(12, Font.BOLD, darkText), Element.ALIGN_CENTER);

        document.add(spacer(6));

        // Dotted line
        addRtlParagraph(document, "...........................................................................",
                getFont(10, Font.NORMAL, Color.GRAY), Element.ALIGN_CENTER);
    }

    // ---- Staff payslip helper methods ----

    private void addFinLabelCell(PdfPTable table, String label, Font font, Color border, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(label), font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(border);
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    private void addFinValueCell(PdfPTable table, String value, Font font, Color border, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(value), font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(border);
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    private BigDecimal staffAdjAmount(StaffPayrollLineResponse line, String type) {
        if (line.getAdjustments() == null) return BigDecimal.ZERO;
        return line.getAdjustments().stream()
                .filter(a -> type.equalsIgnoreCase(a.getAdjustmentType()))
                .map(com.kamal.lc.payroll.dto.StaffAdjustmentResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- Payslip helper methods ----

    private BigDecimal adjAmount(PayrollLineResponse line, String type) {
        if (line.getAdjustments() == null) return BigDecimal.ZERO;
        return line.getAdjustments().stream()
                .filter(a -> type.equals(a.getAdjustmentType()))
                .map(AdjustmentResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void addMetaRow(PdfPTable table, String value, String label, Font valFont, Font lblFont,
                            Color bg, Color border) {
        PdfPCell vc = new PdfPCell(new Phrase(shapeArabic(value), valFont));
        vc.setBorder(Rectangle.BOX);
        vc.setBorderColor(border);
        vc.setPadding(3);
        vc.setHorizontalAlignment(Element.ALIGN_CENTER);
        vc.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(vc);

        PdfPCell lc = new PdfPCell(new Phrase(shapeArabic(label), lblFont));
        lc.setBorder(Rectangle.BOX);
        lc.setBorderColor(border);
        lc.setBackgroundColor(Color.WHITE);
        lc.setPadding(3);
        lc.setHorizontalAlignment(Element.ALIGN_CENTER);
        lc.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(lc);
    }

    private void addStatsRow(PdfPTable table, String value, String label, Font valFont, Font lblFont,
                             Color border) {
        PdfPCell vc = new PdfPCell(new Phrase(shapeArabic(value), valFont));
        vc.setBorder(Rectangle.BOX);
        vc.setBorderColor(border);
        vc.setPadding(5);
        vc.setHorizontalAlignment(Element.ALIGN_CENTER);
        vc.setVerticalAlignment(Element.ALIGN_MIDDLE);
        vc.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(vc);

        PdfPCell lc = new PdfPCell(new Phrase(shapeArabic(label), lblFont));
        lc.setBorder(Rectangle.BOX);
        lc.setBorderColor(border);
        lc.setBackgroundColor(Color.WHITE);
        lc.setPadding(5);
        lc.setHorizontalAlignment(Element.ALIGN_CENTER);
        lc.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lc.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(lc);
    }

    private void addBarCell(PdfPTable table, double value, double maxVal, Color barColor,
                            String label, Font font) {
        PdfPCell barCell = new PdfPCell();
        barCell.setBorder(Rectangle.NO_BORDER);
        barCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        barCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        barCell.setPadding(2);
        barCell.setMinimumHeight(100);

        // Create a nested table with bar + label
        PdfPTable barInner = new PdfPTable(1);
        barInner.setWidthPercentage(80);

        // Top spacer (inverse proportion of bar height)
        double fraction = Math.min(value / maxVal, 1.0);
        int barHeightPx = Math.max((int) (80 * fraction), 5);
        int spacerHeight = 80 - barHeightPx;

        if (spacerHeight > 0) {
            PdfPCell spacerCell = new PdfPCell(new Phrase("", font));
            spacerCell.setBorder(Rectangle.NO_BORDER);
            spacerCell.setMinimumHeight(spacerHeight);
            barInner.addCell(spacerCell);
        }

        // Bar rectangle
        PdfPCell rectCell = new PdfPCell(new Phrase(String.valueOf((int) value), getFont(6, Font.BOLD, Color.WHITE)));
        rectCell.setBackgroundColor(barColor);
        rectCell.setMinimumHeight(barHeightPx);
        rectCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        rectCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        rectCell.setBorder(Rectangle.NO_BORDER);
        barInner.addCell(rectCell);

        // Label
        PdfPCell lblCell = new PdfPCell(new Phrase(label, font));
        lblCell.setBorder(Rectangle.NO_BORDER);
        lblCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        lblCell.setPaddingTop(2);
        barInner.addCell(lblCell);

        barCell.addElement(barInner);
        table.addCell(barCell);
    }

    private void addBorderedCell(PdfPTable table, String text, Font font, Color border,
                                  int alignment, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? shapeArabic(text) : "—", font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(border);
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    private void addSalaryLabelCell(PdfPTable table, String label, Font font, Color border) {
        PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(label), font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(border);
        cell.setBackgroundColor(Color.WHITE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    private void addSalaryValueCell(PdfPTable table, String value, Font font, Color border) {
        PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(value), font));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(border);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(height);
        return p;
    }

    private String arabicMonthName(int month) {
        return switch (month) {
            case 1 -> "يناير";
            case 2 -> "فبراير";
            case 3 -> "مارس";
            case 4 -> "أبريل";
            case 5 -> "مايو";
            case 6 -> "يونيو";
            case 7 -> "يوليو";
            case 8 -> "أغسطس";
            case 9 -> "سبتمبر";
            case 10 -> "أكتوبر";
            case 11 -> "نوفمبر";
            case 12 -> "ديسمبر";
            default -> "";
        };
    }

    // =========================================================================
    // Classic White Layout Helper Methods
    // =========================================================================

    /**
     * Classic company masthead for captain payroll PDFs — matches the organisational form.
     * Layout: Logo on the left | Company info on the right (RTL)
     */
    private void addClassicMasthead(Document document, PayrollRunResponse run) throws DocumentException {
        Color borderColor = new Color(200, 200, 200);
        Color darkText = new Color(30, 30, 30);

        Font fontCompany = getFont(12, Font.BOLD, darkText);
        Font fontDept = getFont(9, Font.NORMAL, darkText);
        Font fontTitle = getFont(11, Font.BOLD, darkText);

        LocalDate periodFrom = LocalDate.parse(run.getPeriodFrom());
        String monthNameAr = arabicMonthName(periodFrom.getMonthValue());
        int monthNum = periodFrom.getMonthValue();
        int year = periodFrom.getYear();

        PdfPTable masthead = new PdfPTable(2);
        masthead.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        masthead.setWidthPercentage(100);
        masthead.setWidths(new float[]{70, 30});

        // Right cell (RTL): Company info
        Phrase companyPhrase = new Phrase();
        companyPhrase.add(new Chunk(shapeArabic(ExcelBranding.COMPANY_NAME) + "\n", fontCompany));
        companyPhrase.add(new Chunk(shapeArabic("الادارة العامة") + "\n", fontDept));
        companyPhrase.add(new Chunk(shapeArabic("قسم شؤون الموظفين") + "\n", fontDept));
        companyPhrase.add(new Chunk(shapeArabic("مسيرات الرواتب شهر " + monthNameAr + " "
                + String.format("%02d", monthNum) + "-" + year), fontTitle));

        PdfPCell infoCell = new PdfPCell(companyPhrase);
        infoCell.setBorder(Rectangle.BOX);
        infoCell.setBorderColor(borderColor);
        infoCell.setPadding(10);
        infoCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        masthead.addCell(infoCell);

        // Left cell (RTL): Logo
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.BOX);
        logoCell.setBorderColor(borderColor);
        logoCell.setPadding(10);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        byte[] logoBytes = loadLogoBytes();
        if (logoBytes != null) {
            try {
                Image img = Image.getInstance(logoBytes);
                img.setAlignment(Element.ALIGN_CENTER);
                img.scaleToFit(100, 60);
                logoCell.addElement(img);
            } catch (Exception e) {
                log.warn("Failed to add logo to masthead", e);
            }
        }
        masthead.addCell(logoCell);

        document.add(masthead);
        document.add(spacer(8));
    }

    /**
     * Classic company masthead for staff payroll PDFs (workers/managers).
     */
    private void addClassicStaffMasthead(Document document, PayrollRunResponse run, String typeAr) throws DocumentException {
        Color borderColor = new Color(200, 200, 200);
        Color darkText = new Color(30, 30, 30);

        Font fontCompany = getFont(12, Font.BOLD, darkText);
        Font fontDept = getFont(9, Font.NORMAL, darkText);
        Font fontTitle = getFont(11, Font.BOLD, darkText);

        LocalDate periodFrom = LocalDate.parse(run.getPeriodFrom());
        String monthNameAr2 = arabicMonthName(periodFrom.getMonthValue());
        int monthNum = periodFrom.getMonthValue();
        int year = periodFrom.getYear();

        PdfPTable masthead = new PdfPTable(2);
        masthead.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        masthead.setWidthPercentage(100);
        masthead.setWidths(new float[]{70, 30});

        Phrase companyPhrase = new Phrase();
        companyPhrase.add(new Chunk(shapeArabic(ExcelBranding.COMPANY_NAME) + "\n", fontCompany));
        companyPhrase.add(new Chunk(shapeArabic("الادارة العامة") + "\n", fontDept));
        companyPhrase.add(new Chunk(shapeArabic("قسم شؤون الموظفين") + "\n", fontDept));
        companyPhrase.add(new Chunk(shapeArabic("مسيرات رواتب " + typeAr + " شهر " + monthNameAr2
                + " " + String.format("%02d", monthNum) + "-" + year), fontTitle));

        PdfPCell infoCell = new PdfPCell(companyPhrase);
        infoCell.setBorder(Rectangle.BOX);
        infoCell.setBorderColor(borderColor);
        infoCell.setPadding(10);
        infoCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        masthead.addCell(infoCell);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.BOX);
        logoCell.setBorderColor(borderColor);
        logoCell.setPadding(10);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        byte[] logoBytes = loadLogoBytes();
        if (logoBytes != null) {
            try {
                Image img = Image.getInstance(logoBytes);
                img.setAlignment(Element.ALIGN_CENTER);
                img.scaleToFit(100, 60);
                logoCell.addElement(img);
            } catch (Exception e) {
                log.warn("Failed to add logo to staff masthead", e);
            }
        }
        masthead.addCell(logoCell);

        document.add(masthead);
        document.add(spacer(8));
    }

    /** Classic header row — light gray background with black text */
    private void addClassicHeaderRow(PdfPTable table, String[] headers) {
        Color headerBg = new Color(240, 240, 240);
        Color borderColor = new Color(180, 180, 180);
        Font headerFont = getFont(6, Font.BOLD, Color.BLACK);

        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(h), headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(borderColor);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(4);
            cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            table.addCell(cell);
        }
    }

    /** Classic data cell — white background, thin gray borders */
    private void addClassicCell(PdfPTable table, String text) {
        Color borderColor = new Color(200, 200, 200);
        Font dataFont = getFont(6, Font.NORMAL, Color.BLACK);

        PdfPCell cell = new PdfPCell(new Phrase(text != null ? shapeArabic(text) : "—", dataFont));
        cell.setBackgroundColor(Color.WHITE);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(borderColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(3);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    /** Classic total row cell — slightly tinted background */
    private void addClassicTotalCell(PdfPTable table, String text, int colspan) {
        Color totalBg = new Color(245, 245, 245);
        Color borderColor = new Color(180, 180, 180);
        Font totalFont = getFont(6, Font.BOLD, Color.BLACK);

        PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(text), totalFont));
        cell.setColspan(colspan);
        cell.setBackgroundColor(totalBg);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(borderColor);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    // =========================================================================
    // Helper Layout Methods
    // =========================================================================
    private PdfPTable createRtlTable(float[] widths) throws DocumentException {
        PdfPTable table = new PdfPTable(widths.length);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        return table;
    }

    private void addHeaderTable(Document doc, String title, String subtitle) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{ 75, 25 });

        Phrase headerPhrase = new Phrase();
        headerPhrase.add(new Chunk(shapeArabic(ExcelBranding.COMPANY_NAME) + "\n", getFont(10, Font.BOLD, new Color(71, 85, 105))));
        headerPhrase.add(new Chunk(shapeArabic(title) + "\n", getFont(15, Font.BOLD, new Color(15, 23, 42))));
        headerPhrase.add(new Chunk(shapeArabic(subtitle), getFont(9, Font.NORMAL, new Color(100, 116, 139))));
        PdfPCell textCell = new PdfPCell(headerPhrase);
        textCell.setBorder(Rectangle.NO_BORDER);
        textCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(textCell);

        PdfPCell imgCell = new PdfPCell();
        imgCell.setBorder(Rectangle.NO_BORDER);
        imgCell.setHorizontalAlignment(Element.ALIGN_LEFT);

        byte[] logoBytes = loadLogoBytes();
        if (logoBytes != null) {
            try {
                Image img = Image.getInstance(logoBytes);
                img.scaleToFit(110, 45);
                imgCell.addElement(img);
            } catch (Exception e) {
                log.warn("Logo add failed", e);
            }
        }
        table.addCell(imgCell);

        doc.add(table);
        doc.add(new Paragraph(" "));
    }

    private void addHeaderRow(PdfPTable table, String[] headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(h), getFont(8, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(new Color(30, 41, 59));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5);
            cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text, boolean num) {
        addCell(table, text, num, false);
    }

    private void addCell(PdfPTable table, String text, boolean num, boolean bold) {
        Font font = getFont(8, bold ? Font.BOLD : Font.NORMAL, Color.BLACK);
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? shapeArabic(text) : "—", font));
        cell.setHorizontalAlignment(num ? Element.ALIGN_CENTER : Element.ALIGN_RIGHT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    private void addTotalCell(PdfPTable table, String text, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(text), getFont(8, Font.BOLD, new Color(15, 23, 42))));
        cell.setColspan(colspan);
        cell.setBackgroundColor(new Color(241, 245, 249));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    private void addInfoCell(PdfPTable table, String label, String val) {
        PdfPCell lCell = new PdfPCell(new Phrase(shapeArabic(label), getFont(8, Font.BOLD, new Color(71, 85, 105))));
        lCell.setBackgroundColor(new Color(248, 250, 252));
        lCell.setPadding(5);
        lCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(lCell);

        PdfPCell vCell = new PdfPCell(new Phrase(val != null ? shapeArabic(val) : "—", getFont(9, Font.BOLD, new Color(15, 23, 42))));
        vCell.setPadding(5);
        vCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(vCell);
    }

    private void addPerfCell(PdfPTable table, String label, String val) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(shapeArabic(label) + "\n", getFont(7, Font.NORMAL, new Color(100, 116, 139))));
        phrase.add(new Chunk(shapeArabic(val), getFont(11, Font.BOLD, new Color(15, 23, 42))));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(new Color(241, 245, 249));
        cell.setPadding(6);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    private void addPayslipRow(PdfPTable table, String label, String amount) {
        addPayslipRow(table, label, amount, false);
    }

    private void addPayslipRow(PdfPTable table, String label, String amount, boolean isDeduction) {
        PdfPCell c1 = new PdfPCell(new Phrase(shapeArabic(label), getFont(9, Font.NORMAL, Color.BLACK)));
        c1.setPadding(6);
        c1.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(c1);

        Color amtColor = isDeduction ? new Color(220, 38, 38) : new Color(15, 23, 42);
        PdfPCell c2 = new PdfPCell(new Phrase(amount, getFont(9, Font.BOLD, amtColor)));
        c2.setHorizontalAlignment(Element.ALIGN_LEFT);
        c2.setPadding(6);
        c2.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(c2);
    }

    /**
     * Adds the official payroll signatures block: HR, Finance Manager, and Executive Manager.
     * This is appended to every payroll PDF report.
     */
    private void addOfficialSignatures(Document document) throws DocumentException {
        document.add(spacer(20));

        Font labelFont = getFont(9, Font.BOLD, Color.BLACK);
        Font lineFont = getFont(9, Font.NORMAL, Color.DARK_GRAY);

        PdfPTable signTable = new PdfPTable(3);
        signTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signTable.setWidthPercentage(90);
        signTable.setWidths(new float[]{33, 34, 33});
        signTable.setHorizontalAlignment(Element.ALIGN_CENTER);

        // Row 1: Title labels
        addSignatureLabel(signTable, "شؤون الموظفين\nHR Department", labelFont);
        addSignatureLabel(signTable, "المدير المالي\nFinance Manager", labelFont);
        addSignatureLabel(signTable, "المدير التنفيذي\nExecutive Manager", labelFont);

        // Row 2: Signature lines
        addSignatureLabel(signTable, "\n\n_______________________", lineFont);
        addSignatureLabel(signTable, "\n\n_______________________", lineFont);
        addSignatureLabel(signTable, "\n\n_______________________", lineFont);

        // Row 3: Name placeholder
        addSignatureLabel(signTable, "الاسم / Name: ..................", lineFont);
        addSignatureLabel(signTable, "الاسم / Name: ..................", lineFont);
        addSignatureLabel(signTable, "الاسم / Name: ..................", lineFont);

        // Row 4: Date placeholder
        addSignatureLabel(signTable, "التاريخ / Date: ....../....../......", lineFont);
        addSignatureLabel(signTable, "التاريخ / Date: ....../....../......", lineFont);
        addSignatureLabel(signTable, "التاريخ / Date: ....../....../......", lineFont);

        document.add(signTable);
    }

    private void addSignatureLabel(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(text), font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(4);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(cell);
    }

    private String fmtMoney(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }

    private String fmt(BigDecimal val) {
        if (val == null) return "0";
        return val.setScale(1, java.math.RoundingMode.HALF_UP).toString();
    }

    private BigDecimal nz(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}
