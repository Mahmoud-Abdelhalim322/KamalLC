package com.kamal.lc.captain.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.export.ExcelBranding;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;

/**
 * Exports captain data as branded PDF documents with company logo and export date.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MembersPdfExportService {

    private final CaptainRepository captainRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private BaseFont baseFont;

    private BaseFont getArabicBaseFont() {
        if (baseFont == null) {
            try {
                InputStream is = new ClassPathResource("fonts/Cairo-Regular.ttf").getInputStream();
                byte[] fontBytes = is.readAllBytes();
                baseFont = BaseFont.createFont("Cairo-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
            } catch (Exception e) {
                log.warn("Could not load Cairo font: {}", e.getMessage());
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

    private Font getFont(float size, int style, Color color) {
        return new Font(getArabicBaseFont(), size, style, color);
    }

    private Font getFont(float size, int style) {
        return getFont(size, style, Color.BLACK);
    }

    private String shapeArabic(String text) {
        if (text == null || text.isEmpty()) return text;
        try {
            ArabicShaping shaping = new ArabicShaping(ArabicShaping.LETTERS_SHAPE);
            return shaping.shape(text);
        } catch (ArabicShapingException e) {
            return text;
        }
    }

    private byte[] loadLogoBytes() {
        try (InputStream is = new ClassPathResource("branding/logo-ksclc.png").getInputStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.warn("Could not load logo: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Exports captains as a PDF file.
     */
    public byte[] exportCaptainsPdf(String type) {
        List<Captain> captains = fetchAll(type);
        String title = resolveTitle(type);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // A4 Landscape for wide table
            Document document = new Document(PageSize.A4.rotate(), 10, 10, 10, 10);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // --- Masthead ---
            addMasthead(document, title);

            // --- Data Table ---
            String[] headers = {
                    "م", "الكود", "نوع العقد", "الاسم بالعربي", "الجنسية", "الهاتف",
                    "رقم الإقامة", "انتهاء الإقامة", "رقم الجواز", "انتهاء الجواز",
                    "رقم الرخصة", "انتهاء الرخصة", "انتهاء رخصة العمل",
                    "رقم التأمين", "انتهاء التأمين", "الراتب الأساسي",
                    "الحالة", "تاريخ الالتحاق"
            };
            float[] widths = { 3, 6, 5, 12, 6, 7, 7, 6, 7, 6, 6, 6, 6, 6, 6, 6, 5, 6 };

            PdfPTable table = new PdfPTable(widths.length);
            table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            table.setWidthPercentage(100);
            table.setWidths(widths);

            // Header row
            Color headerBg = new Color(240, 240, 240);
            Font headerFont = getFont(7, Font.BOLD);
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(h), headerFont));
                cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setBackgroundColor(headerBg);
                cell.setPadding(4);
                table.addCell(cell);
            }

            // Data rows
            Font dataFont = getFont(6.5f, Font.NORMAL);
            int seq = 1;
            for (Captain c : captains) {
                addPdfCell(table, String.valueOf(seq++), dataFont);
                addPdfCell(table, c.getKamalCode(), dataFont);
                addPdfCell(table, captainTypeLabel(c.getCaptainType()), dataFont);
                addPdfCell(table, c.getFullNameAr(), dataFont);
                addPdfCell(table, c.getNationality(), dataFont);
                addPdfCell(table, c.getPhone(), dataFont);
                addPdfCell(table, c.getIqamaNumber(), dataFont);
                addPdfCell(table, fmtDate(c.getIqamaExpiry()), dataFont);
                addPdfCell(table, c.getPassportNumber(), dataFont);
                addPdfCell(table, fmtDate(c.getPassportExpiry()), dataFont);
                addPdfCell(table, c.getDrivingLicenceNo(), dataFont);
                addPdfCell(table, fmtDate(c.getLicenceExpiry()), dataFont);
                addPdfCell(table, fmtDate(c.getWorkPermitExpiry()), dataFont);
                addPdfCell(table, c.getInsuranceNumber(), dataFont);
                addPdfCell(table, fmtDate(c.getInsuranceExpiry()), dataFont);
                addPdfCell(table, fmtMoney(c.getBasicSalary()), dataFont);
                addPdfCell(table, statusLabel(c.getStatus()), dataFont);
                addPdfCell(table, fmtDate(c.getJoinDate()), dataFont);
            }

            document.add(table);

            // Summary
            document.add(spacer(8));
            Font summaryFont = getFont(9, Font.BOLD);
            PdfPTable summaryTable = new PdfPTable(1);
            summaryTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            summaryTable.setWidthPercentage(100);
            PdfPCell summaryCell = new PdfPCell(new Phrase(
                    shapeArabic("إجمالي عدد الكباتن: " + captains.size()), summaryFont));
            summaryCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            summaryCell.setBorder(Rectangle.NO_BORDER);
            summaryCell.setPadding(4);
            summaryTable.addCell(summaryCell);
            document.add(summaryTable);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build captains PDF export", e);
            throw new RuntimeException("فشل في تصدير ملف PDF", e);
        }
    }

    private void addMasthead(Document document, String title) throws DocumentException {
        Color borderColor = new Color(200, 200, 200);
        Color darkText = new Color(30, 30, 30);

        Font fontCompany = getFont(12, Font.BOLD, darkText);
        Font fontDept = getFont(9, Font.NORMAL, darkText);
        Font fontTitle = getFont(11, Font.BOLD, darkText);
        Font fontDate = getFont(8, Font.NORMAL, darkText);

        PdfPTable masthead = new PdfPTable(2);
        masthead.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        masthead.setWidthPercentage(100);
        masthead.setWidths(new float[]{70, 30});

        // Right cell: Company info
        Phrase companyPhrase = new Phrase();
        companyPhrase.add(new Chunk(shapeArabic(ExcelBranding.COMPANY_NAME) + "\n", fontCompany));
        companyPhrase.add(new Chunk(shapeArabic("الادارة العامة") + "\n", fontDept));
        companyPhrase.add(new Chunk(shapeArabic("قسم شؤون الموظفين") + "\n", fontDept));
        companyPhrase.add(new Chunk(shapeArabic(title) + "\n", fontTitle));
        companyPhrase.add(new Chunk(shapeArabic("تاريخ التصدير: " + LocalDate.now().format(DATE_FMT)), fontDate));

        PdfPCell infoCell = new PdfPCell(companyPhrase);
        infoCell.setBorder(Rectangle.BOX);
        infoCell.setBorderColor(borderColor);
        infoCell.setPadding(10);
        infoCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        masthead.addCell(infoCell);

        // Left cell: Logo
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
                log.warn("Failed to add logo to PDF masthead", e);
            }
        }
        masthead.addCell(logoCell);

        document.add(masthead);
        document.add(spacer(8));
    }

    private void addPdfCell(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(shapeArabic(value != null ? value : "—"), font));
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(3);
        table.addCell(cell);
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }

    private List<Captain> fetchAll(String type) {
        if (type != null && !type.isBlank()) {
            return captainRepo.findByCaptainType(type.toUpperCase(),
                    org.springframework.data.domain.PageRequest.of(0, 10000, Sort.by("fullNameAr"))).getContent();
        }
        return captainRepo.findAll(Sort.by("fullNameAr"));
    }

    private String resolveTitle(String type) {
        if ("EMPLOYEE".equalsIgnoreCase(type)) return "قائمة الكباتن (KSC)";
        if ("FREELANCE".equalsIgnoreCase(type)) return "قائمة الكباتن (KSCFL)";
        return "قائمة جميع الكباتن";
    }

    private String captainTypeLabel(String type) {
        if ("EMPLOYEE".equalsIgnoreCase(type)) return "موظف";
        if ("FREELANCE".equalsIgnoreCase(type)) return "أجير";
        return type;
    }

    private String statusLabel(String status) {
        if ("ACTIVE".equalsIgnoreCase(status)) return "نشط";
        if ("INACTIVE".equalsIgnoreCase(status)) return "غير نشط";
        if ("TERMINATED".equalsIgnoreCase(status)) return "منتهي";
        return status;
    }

    private String fmtDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "—";
    }

    private String fmtMoney(BigDecimal value) {
        return value != null ? String.format("%,.2f", value) : "—";
    }
}
