package com.kamal.lc.custody.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.custody.dto.CaptainCustodySummary;
import com.kamal.lc.custody.dto.CustodyRecordResponse;
import com.kamal.lc.fleet.entity.Vehicle;
import com.kamal.lc.fleet.entity.VehicleAssignment;
import com.kamal.lc.fleet.repository.VehicleAssignmentRepository;
import com.kamal.lc.inventory.repository.AssetItemRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Generates official custody handover PDF:
 *   - Page 1: Official Handover Form (Header + Captain Info + Custody Items + Car Diagram)
 *   - Page 2: Terms & Conditions (16-point bilingual table) + Legal Undertaking + Signatures
 */
@Slf4j
@Service
public class CustodyHandoverPdfService {

    private final CustodyService custodyService;
    private final CaptainRepository captainRepository;
    private final VehicleAssignmentRepository vehicleAssignmentRepository;
    private final AssetItemRepository assetItemRepository;

    private BaseFont baseFont;

    public CustodyHandoverPdfService(CustodyService custodyService) {
        this(custodyService, null, null, null);
    }

    @Autowired
    public CustodyHandoverPdfService(CustodyService custodyService,
                                     @Autowired(required = false) CaptainRepository captainRepository,
                                     @Autowired(required = false) VehicleAssignmentRepository vehicleAssignmentRepository,
                                     @Autowired(required = false) AssetItemRepository assetItemRepository) {
        this.custodyService = custodyService;
        this.captainRepository = captainRepository;
        this.vehicleAssignmentRepository = vehicleAssignmentRepository;
        this.assetItemRepository = assetItemRepository;
    }

    // ──────────────────── Font helpers ────────────────────

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
                String[] paths = { "C:\\Windows\\Fonts\\arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf" };
                for (String p : paths) {
                    if (new File(p).exists()) {
                        try { baseFont = BaseFont.createFont(p, BaseFont.IDENTITY_H, BaseFont.EMBEDDED); break; }
                        catch (Exception ex) { /* try next */ }
                    }
                }
            }
            if (baseFont == null) {
                try { baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED); }
                catch (Exception e) { log.error("No font available", e); }
            }
        }
        return baseFont;
    }

    private Font font(float size, int style, Color color) {
        return new Font(getArabicBaseFont(), size, style, color);
    }
    private Font font(float size, int style) { return font(size, style, Color.BLACK); }
    private Font font(float size) { return font(size, Font.NORMAL, Color.BLACK); }

    private String shape(String text) {
        if (text == null || text.isEmpty()) return text;
        try { return new ArabicShaping(ArabicShaping.LETTERS_SHAPE).shape(text); }
        catch (ArabicShapingException e) { return text; }
    }

    // ──────────────────── Public API ────────────────────

    /**
     * Builds a balanced 2-page handover PDF:
     *   - Page 1: Form with captain info, custody details, and side-by-side car diagram
     *   - Page 2: Terms & Conditions (16 points) and 3-party signatures
     */
    @Transactional(readOnly = true)
    public byte[] buildHandoverPdf(UUID captainId) {
        CaptainCustodySummary summary = custodyService.getCaptainSummaries().stream()
                .filter(s -> s.getCaptainId().equals(captainId))
                .findFirst()
                .orElse(null);

        Captain captain = null;
        if (captainRepository != null) {
            try {
                captain = captainRepository.findById(captainId).orElse(null);
                if (captain == null && summary != null && summary.getCaptainCode() != null) {
                    captain = captainRepository.findByKamalCode(summary.getCaptainCode()).orElse(null);
                }
            } catch (Throwable t) {
                log.warn("Could not load captain entity: {}", t.getMessage(), t);
            }
        }

        if (summary == null) {
            if (captain == null) {
                throw new IllegalArgumentException("لا توجد عهد نشطة أو بيانات لهذا الكابتن");
            }
            summary = CaptainCustodySummary.builder()
                    .captainId(captain.getId())
                    .captainCode(captain.getKamalCode())
                    .captainName(captain.getFullNameAr())
                    .activeCount(0)
                    .activeItems(java.util.Collections.emptyList())
                    .build();
        }

        List<CustodyRecordResponse> items = summary.getActiveItems() != null ? summary.getActiveItems() : java.util.Collections.emptyList();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Balanced page margins
            Document doc = new Document(PageSize.A4, 18, 18, 16, 16);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            Color borderColor = new Color(50, 50, 50);
            Color darkText = new Color(20, 20, 20);
            Color brandGreen = new Color(0, 120, 60);

            // ════════════════════════════════════════════════════════════════
            // ─── PAGE 1: OFFICIAL FORM + CAPTAIN INFO + CUSTODY & CAR ───
            // ════════════════════════════════════════════════════════════════

            // 1. Top Header Box
            addFormHeader(doc, LocalDate.now(), borderColor, darkText);
            doc.add(spacer(4));

            // 2. Job ID Bar
            addJobIdBar(doc, summary.getCaptainCode(), borderColor, darkText);

            // 3. Captain Info Table
            addCaptainTable(doc, captain, summary, borderColor, darkText);
            doc.add(spacer(5));

            // 4. Custody Categories & Car Diagram (Side-by-Side)
            addCustodyAndCarSection(doc, items, borderColor, darkText);
            doc.add(spacer(4));

            // 5. Page 1 Inspection & Initial Handover Bar (fills page 1 completely without empty void)
            addPage1InspectionBar(doc, borderColor, darkText);

            // ════════════════════════════════════════════════════════════════
            // ─── PAGE 2: TERMS & CONDITIONS + LEGAL NOTE + SIGNATURES ───
            // ════════════════════════════════════════════════════════════════
            doc.newPage();

            // Page 2 Header (Strictly Black & White for printing)
            addPage2Header(doc, LocalDate.now(), Color.BLACK, Color.BLACK);
            doc.add(spacer(6));

            // 16-point Terms & Conditions Table (Pure White background, Black borders/text)
            addTermsTable(doc, Color.BLACK, Color.BLACK);
            doc.add(spacer(6));

            // Legal Undertaking Box (White background, Black border/text)
            addLegalUndertaking(doc, Color.BLACK, Color.BLACK);
            doc.add(spacer(6));

            // Official Signatures (White backgrounds, Black borders/text, spacious for signing)
            addSignaturesTable(doc, summary.getCaptainName(), Color.BLACK, Color.BLACK);
            doc.add(spacer(4));

            // Footer
            addPage2Footer(doc);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build custody handover PDF", e);
            throw new RuntimeException("فشل في إنشاء محضر تسليم العهدة", e);
        }
    }

    // ──────────────────── Page 1 Components ────────────────────

    private void addFormHeader(Document doc, LocalDate date, Color borderColor, Color darkText) throws DocumentException {
        PdfPTable header = new PdfPTable(3);
        header.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        header.setWidthPercentage(100);
        // RTL columns: [Cell 0: Right (Metadata 32%)], [Cell 1: Center (Title 46%)], [Cell 2: Left (Logo 22%)]
        header.setWidths(new float[]{22f, 46f, 32f});

        // Cell 0: Right - Metadata Box (nested table)
        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        metaTable.setWidthPercentage(100);
        metaTable.setWidths(new float[]{55f, 45f});

        addMetaRow(metaTable, "رقم النموذج\nModel Number", "HMRLS - HR - 0010", borderColor);
        addMetaRow(metaTable, "نوع النموذج\nModel Type", "استلام وتسليم عهد\nHANDOVER & ACCEPTANCE", borderColor);
        addMetaRow(metaTable, "التاريخ\nDate", date.format(DateTimeFormatter.ISO_LOCAL_DATE), borderColor);

        PdfPCell metaCell = new PdfPCell(metaTable);
        metaCell.setBorder(Rectangle.BOX);
        metaCell.setBorderColor(borderColor);
        metaCell.setPadding(0);
        header.addCell(metaCell);

        // Cell 1: Center - Title
        Phrase titlePhrase = new Phrase();
        titlePhrase.add(new Chunk(shape("استلام وتسليم العهدة\n"), font(13.5f, Font.BOLD, darkText)));
        titlePhrase.add(new Chunk("CUSTODY HANDOVER & ACCEPTANCE", font(8.5f, Font.BOLD, new Color(0, 120, 60))));
        PdfPCell titleCell = new PdfPCell(titlePhrase);
        titleCell.setBorder(Rectangle.BOX);
        titleCell.setBorderColor(borderColor);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setPadding(6);
        titleCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        header.addCell(titleCell);

        // Cell 2: Left - Logo
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.BOX);
        logoCell.setBorderColor(borderColor);
        logoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        logoCell.setPadding(4);
        try {
            byte[] logoBytes = new ClassPathResource("branding/logo-ksclc.png").getInputStream().readAllBytes();
            Image img = Image.getInstance(logoBytes);
            img.scaleToFit(95, 46);
            img.setAlignment(Element.ALIGN_CENTER);
            logoCell.addElement(img);
        } catch (Exception e) {
            logoCell.addElement(new Phrase("kslsc", font(13, Font.BOLD, new Color(0, 120, 60))));
        }
        header.addCell(logoCell);

        doc.add(header);
    }

    private void addMetaRow(PdfPTable table, String label, String val, Color borderColor) {
        PdfPCell valCell = new PdfPCell(new Phrase(shape(val), font(7f, Font.BOLD)));
        valCell.setBorder(Rectangle.BOX);
        valCell.setBorderColor(borderColor);
        valCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        valCell.setPadding(3f);
        valCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        PdfPCell lblCell = new PdfPCell(new Phrase(shape(label), font(6.5f, Font.BOLD, Color.DARK_GRAY)));
        lblCell.setBorder(Rectangle.BOX);
        lblCell.setBorderColor(borderColor);
        lblCell.setBackgroundColor(new Color(245, 245, 245));
        lblCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        lblCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lblCell.setPadding(3f);
        lblCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        table.addCell(lblCell);
        table.addCell(valCell);
    }

    private void addJobIdBar(Document doc, String captainCode, Color borderColor, Color darkText) throws DocumentException {
        PdfPTable bar = new PdfPTable(2);
        bar.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        bar.setWidthPercentage(36);
        bar.setHorizontalAlignment(Element.ALIGN_RIGHT);
        bar.setWidths(new float[]{55f, 45f});

        PdfPCell lblCell = new PdfPCell(new Phrase(shape("الرقم الوظيفي\nJob ID"), font(7.5f, Font.BOLD, darkText)));
        lblCell.setBorder(Rectangle.BOX);
        lblCell.setBorderColor(borderColor);
        lblCell.setBackgroundColor(new Color(245, 245, 245));
        lblCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        lblCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lblCell.setPadding(3.5f);
        lblCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        bar.addCell(lblCell);

        PdfPCell valCell = new PdfPCell(new Phrase(captainCode != null ? captainCode : "—", font(9.5f, Font.BOLD, darkText)));
        valCell.setBorder(Rectangle.BOX);
        valCell.setBorderColor(borderColor);
        valCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        valCell.setPadding(3.5f);
        bar.addCell(valCell);

        doc.add(bar);
    }

    private void addCaptainTable(Document doc, Captain captain, CaptainCustodySummary summary,
                                 Color borderColor, Color darkText) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.setWidthPercentage(100);
        // RTL columns: [Cell 0: Right (Captain Header 16%)], [Cell 1: Left (Fields Table 84%)]
        table.setWidths(new float[]{84f, 16f});

        // Far-Right column: الكابتن / CAPTAIN [✓]
        Phrase capPhrase = new Phrase();
        capPhrase.add(new Chunk(shape("الكابتن\nCAPTAIN\n\n"), font(8.5f, Font.BOLD, darkText)));
        capPhrase.add(new Chunk(shape("[ ✓ ]"), font(10f, Font.BOLD, new Color(0, 120, 60))));
        PdfPCell capCell = new PdfPCell(capPhrase);
        capCell.setBorder(Rectangle.BOX);
        capCell.setBorderColor(borderColor);
        capCell.setBackgroundColor(new Color(248, 250, 252));
        capCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        capCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        capCell.setPadding(4f);
        capCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(capCell);

        // Fields sub-table (6 rows)
        PdfPTable fieldsTable = new PdfPTable(2);
        fieldsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        fieldsTable.setWidthPercentage(100);
        // RTL columns: [Cell 0: Right (Label 28%)], [Cell 1: Left (Value 72%)]
        fieldsTable.setWidths(new float[]{72f, 28f});

        String arName = summary.getCaptainName();
        String enName = "";
        String nat = "—";
        String idNum = "—";
        String dob = "—";
        String phone = "—";

        if (captain != null) {
            try {
                if (captain.getFullNameAr() != null && !captain.getFullNameAr().isBlank()) arName = captain.getFullNameAr();
                if (captain.getFullNameEn() != null && !captain.getFullNameEn().isBlank()) enName = captain.getFullNameEn();
                if (captain.getNationality() != null && !captain.getNationality().isBlank()) nat = captain.getNationality();
                if (captain.getIqamaNumber() != null && !captain.getIqamaNumber().isBlank()) idNum = captain.getIqamaNumber();
                else if (captain.getAppId() != null && !captain.getAppId().isBlank()) idNum = captain.getAppId();
                else if (captain.getPassportNumber() != null && !captain.getPassportNumber().isBlank()) idNum = captain.getPassportNumber();
                if (captain.getDateOfBirth() != null) dob = captain.getDateOfBirth().toString();
                if (captain.getPhone() != null && !captain.getPhone().isBlank()) phone = captain.getPhone();
            } catch (Throwable t) {
                log.warn("Could not read captain fields: {}", t.getMessage());
            }
        }

        // Row 1: Name
        Phrase namePhrase = new Phrase();
        namePhrase.add(new Chunk(shape(arName), font(8.5f, Font.BOLD, darkText)));
        if (!enName.isEmpty()) {
            namePhrase.add(new Chunk("  (" + enName + ")", font(7.5f, Font.NORMAL, Color.DARK_GRAY)));
        }
        addCaptainRow(fieldsTable, "الاســـــــــــــم\nNAME", namePhrase, borderColor);

        // Row 2: Nationality
        addCaptainRow(fieldsTable, "الجنسية", new Phrase(shape(nat), font(8f, Font.BOLD, darkText)), borderColor);

        // Row 3: National ID / Iqama
        addCaptainRow(fieldsTable, "رقم الهوية/الإقامة", new Phrase(idNum, font(8.5f, Font.BOLD, darkText)), borderColor);

        // Row 4: Birth date
        addCaptainRow(fieldsTable, "تاريخ الميلاد", new Phrase(dob, font(8f, Font.BOLD, darkText)), borderColor);

        // Row 5: Mobile
        addCaptainRow(fieldsTable, "رقم الجوال الشخصي", new Phrase(phone, font(8.5f, Font.BOLD, darkText)), borderColor);

        // Row 6: Company
        String comp = "شركة كمال بن خلف السلمي للخدمات اللوجستية";
        addCaptainRow(fieldsTable, "اسم الشركة التابع لها", new Phrase(shape(comp), font(8f, Font.BOLD, darkText)), borderColor);

        PdfPCell fieldsCell = new PdfPCell(fieldsTable);
        fieldsCell.setBorder(Rectangle.BOX);
        fieldsCell.setBorderColor(borderColor);
        fieldsCell.setPadding(0);
        table.addCell(fieldsCell);

        doc.add(table);
    }

    private void addCaptainRow(PdfPTable table, String label, Phrase valPhrase, Color borderColor) {
        PdfPCell lblCell = new PdfPCell(new Phrase(shape(label), font(7.2f, Font.BOLD, Color.DARK_GRAY)));
        lblCell.setBorder(Rectangle.BOX);
        lblCell.setBorderColor(borderColor);
        lblCell.setBackgroundColor(new Color(248, 250, 252));
        lblCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        lblCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lblCell.setPadding(3f);
        lblCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(lblCell);

        PdfPCell valCell = new PdfPCell(valPhrase);
        valCell.setBorder(Rectangle.BOX);
        valCell.setBorderColor(borderColor);
        valCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        valCell.setPadding(3f);
        valCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.addCell(valCell);
    }

    private void addCustodyAndCarSection(Document doc, List<CustodyRecordResponse> items,
                                        Color borderColor, Color darkText) throws DocumentException {
        PdfPTable splitTable = new PdfPTable(2);
        splitTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        splitTable.setWidthPercentage(100);
        // RTL columns: [Cell 0: Right (Custody Categories 57%)], [Cell 1: Left (Car Diagram 43%)]
        splitTable.setWidths(new float[]{43f, 57f});

        // Cell 0 (Right): Custody Categories Table
        PdfPCell custodyCell = new PdfPCell();
        custodyCell.setBorder(Rectangle.BOX);
        custodyCell.setBorderColor(borderColor);
        custodyCell.setPadding(0);

        PdfPTable categoriesTable = buildCategoriesTable(items, borderColor, darkText);
        custodyCell.addElement(categoriesTable);
        splitTable.addCell(custodyCell);

        // Cell 1 (Left): Car Diagram Image & Inspection Note
        PdfPCell carCell = new PdfPCell();
        carCell.setBorder(Rectangle.BOX);
        carCell.setBorderColor(borderColor);
        carCell.setPadding(3f);
        carCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        carCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        try {
            byte[] carBytes = new ClassPathResource("branding/car sheet.jpeg").getInputStream().readAllBytes();
            Image carImg = Image.getInstance(carBytes);
            carImg.scaleToFit(225, 335);
            carImg.setAlignment(Element.ALIGN_CENTER);
            carCell.addElement(carImg);
        } catch (Exception e) {
            log.warn("Could not load car sheet: {}", e.getMessage());
            carCell.addElement(new Phrase(shape("مخطط فحص السيارة"), font(8, Font.ITALIC, Color.GRAY)));
        }

        Paragraph carNote = new Paragraph();
        carNote.setAlignment(Element.ALIGN_CENTER);
        carNote.setSpacingBefore(3f);
        carNote.add(new Chunk(shape("مخطط الفحص الظاهري للمركبة (التأشير على مكان أي صدمات أو خدوش)\n"), font(6.5f, Font.BOLD, Color.DARK_GRAY)));
        carNote.add(new Chunk(shape("الرموز: (X) صدمة  |  (/) خدش  |  (O) كسر  |  (D) تعديل"), font(6.2f, Font.NORMAL, Color.DARK_GRAY)));
        carCell.addElement(carNote);

        splitTable.addCell(carCell);

        doc.add(splitTable);
    }

    private void addPage1InspectionBar(Document doc, Color borderColor, Color darkText) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{45f, 55f});

        Phrase condPhrase = new Phrase();
        condPhrase.add(new Chunk(shape("حالة العهد المسلمة عند الفحص والمعاينة:\n"), font(7.5f, Font.BOLD, darkText)));
        condPhrase.add(new Chunk(shape("[  ] ممتازة وجديدة     [  ] جيدة وصالحة للعمل     [  ] توجد ملاحظات"), font(7f, Font.NORMAL, darkText)));
        PdfPCell condCell = new PdfPCell(condPhrase);
        condCell.setBorder(Rectangle.BOX);
        condCell.setBorderColor(borderColor);
        condCell.setBackgroundColor(new Color(250, 250, 250));
        condCell.setPadding(4f);
        condCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        condCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(condCell);

        Phrase signPhrase = new Phrase();
        signPhrase.add(new Chunk(shape("توقيع المستلم بالمعاينة والاستلام المبدئي: __________________\n"), font(7f, Font.NORMAL, darkText)));
        signPhrase.add(new Chunk(shape("توقيع مسؤول التسليم: __________________  التاريخ: ___/___/2026م"), font(7f, Font.NORMAL, darkText)));
        PdfPCell signCell = new PdfPCell(signPhrase);
        signCell.setBorder(Rectangle.BOX);
        signCell.setBorderColor(borderColor);
        signCell.setBackgroundColor(new Color(250, 250, 250));
        signCell.setPadding(4f);
        signCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(signCell);

        doc.add(table);
    }

    private PdfPTable buildCategoriesTable(List<CustodyRecordResponse> items, Color borderColor, Color darkText) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);

        CustodyRecordResponse vehicleItem = findItem(items, "VEHICLE");
        CustodyRecordResponse phoneItem = findItem(items, "PHONE");
        CustodyRecordResponse simItem = findItem(items, "SIM_CARD");

        // 1. CAR / سيارة
        table.addCell(buildCarCategoryBlock(vehicleItem, borderColor, darkText));

        // 2. MOBIL / جوال
        table.addCell(buildPhoneCategoryBlock(phoneItem, borderColor, darkText));

        // 3. SIM CARD / شريحة
        table.addCell(buildSimCategoryBlock(simItem, borderColor, darkText));

        // 4. UNIFORMS / الزي
        table.addCell(buildUniformCategoryBlock(items, borderColor, darkText));

        // 5. OTHER / أخرى
        table.addCell(buildOtherCategoryBlock(items, borderColor, darkText));

        return table;
    }

    private CustodyRecordResponse findItem(List<CustodyRecordResponse> items, String type) {
        if (items == null) return null;
        return items.stream()
                .filter(i -> type.equalsIgnoreCase(i.getAssetType()))
                .findFirst()
                .orElse(null);
    }

    /** Category 1: CAR / سيارة */
    private PdfPCell buildCarCategoryBlock(CustodyRecordResponse vehicleItem, Color borderColor, Color darkText) {
        boolean hasVehicle = vehicleItem != null;
        String vCode = "—";
        String plate = "—";
        String brand = "—";
        String model = "—";
        String year = "—";
        String odo = "—";
        String serial = "—";

        if (hasVehicle) {
            plate = vehicleItem.getAssetLabel() != null ? vehicleItem.getAssetLabel() : "—";
            if (vehicleAssignmentRepository != null && vehicleItem.getReferenceId() != null) {
                try {
                    VehicleAssignment va = vehicleAssignmentRepository.findById(vehicleItem.getReferenceId()).orElse(null);
                    if (va != null && va.getVehicle() != null) {
                        Vehicle vehicle = va.getVehicle();
                        if (vehicle.getVehicleCode() != null) vCode = vehicle.getVehicleCode();
                        if (vehicle.getPlateNumber() != null) plate = vehicle.getPlateNumber();
                        if (vehicle.getBrand() != null) brand = vehicle.getBrand();
                        if (vehicle.getModel() != null) model = vehicle.getModel();
                        if (vehicle.getYear() != null) year = String.valueOf(vehicle.getYear());
                        if (vehicle.getOdometer() != null) odo = String.valueOf(vehicle.getOdometer());
                        if (vehicle.getVehicleCode() != null) serial = vehicle.getVehicleCode();
                    }
                } catch (Throwable t) {
                    log.warn("Could not load vehicle details: {}", t.getMessage());
                }
            }
        }

        PdfPTable block = new PdfPTable(2);
        block.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.setWidthPercentage(100);
        block.setWidths(new float[]{82f, 18f});

        // Far-Right column: CAR / سيارة [✓]
        PdfPCell catCell = new PdfPCell(categoryHeaderPhrase("CAR / سيارة", hasVehicle));
        catCell.setBorder(Rectangle.BOX);
        catCell.setBorderColor(borderColor);
        catCell.setBackgroundColor(new Color(248, 250, 252));
        catCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        catCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        catCell.setPadding(2f);
        catCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.addCell(catCell);

        // Fields sub-table
        PdfPTable fieldsTable = new PdfPTable(2);
        fieldsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        fieldsTable.setWidthPercentage(100);
        fieldsTable.setWidths(new float[]{65f, 35f});

        addTopIdRow(fieldsTable, "رقم المركبة\nID", vCode, borderColor);
        addCategoryFieldRow(fieldsTable, "رقم اللوحــــــــــــة", plate, borderColor);
        addCategoryFieldRow(fieldsTable, "مـــــاركـــــــــــــة", brand, borderColor);
        addCategoryFieldRow(fieldsTable, "طـــــــــــــــراز", model, borderColor);
        addCategoryFieldRow(fieldsTable, "المـــــــــــــوديــــــل", year, borderColor);
        addCategoryFieldRow(fieldsTable, "رقم العــــــــــــداد", odo, borderColor);

        PdfPCell fieldsCell = new PdfPCell(fieldsTable);
        fieldsCell.setBorder(Rectangle.BOX);
        fieldsCell.setBorderColor(borderColor);
        fieldsCell.setPadding(0);
        block.addCell(fieldsCell);

        PdfPCell outer = new PdfPCell(block);
        outer.setBorder(Rectangle.BOTTOM);
        outer.setBorderColor(borderColor);
        outer.setPadding(0);
        return outer;
    }

    /** Category 2: MOBIL / جوال */
    private PdfPCell buildPhoneCategoryBlock(CustodyRecordResponse phoneItem, Color borderColor, Color darkText) {
        boolean hasPhone = phoneItem != null;
        String phoneCode = "—";
        String phoneType = "—";
        String imei = "—";

        if (hasPhone) {
            // Look up the actual AssetItem from inventory to get proper data
            if (assetItemRepository != null && phoneItem.getReferenceId() != null) {
                try {
                    var assetOpt = assetItemRepository.findById(phoneItem.getReferenceId());
                    if (assetOpt.isPresent()) {
                        var asset = assetOpt.get();
                        phoneCode = asset.getSerialCode() != null ? asset.getSerialCode() : "—";
                        for (var f : asset.getFields()) {
                            if ("phone_type".equals(f.getFieldKey()) && f.getFieldValue() != null && !f.getFieldValue().isBlank())
                                phoneType = f.getFieldValue();
                            if ("imei".equals(f.getFieldKey()) && f.getFieldValue() != null && !f.getFieldValue().isBlank())
                                imei = f.getFieldValue();
                        }
                    }
                } catch (Throwable t) {
                    log.warn("Could not load phone asset details: {}", t.getMessage());
                }
            }
        }

        PdfPTable block = new PdfPTable(2);
        block.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.setWidthPercentage(100);
        block.setWidths(new float[]{82f, 18f});

        PdfPCell catCell = new PdfPCell(categoryHeaderPhrase("MOBIL / جوال", hasPhone));
        catCell.setBorder(Rectangle.BOX);
        catCell.setBorderColor(borderColor);
        catCell.setBackgroundColor(new Color(248, 250, 252));
        catCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        catCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        catCell.setPadding(2f);
        catCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.addCell(catCell);

        PdfPTable fieldsTable = new PdfPTable(2);
        fieldsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        fieldsTable.setWidthPercentage(100);
        fieldsTable.setWidths(new float[]{65f, 35f});

        addTopIdRow(fieldsTable, "رقم الجوال\nID", phoneCode, borderColor);
        addCategoryFieldRow(fieldsTable, "نوع الجـــــــــــوال", phoneType, borderColor);
        addCategoryFieldRow(fieldsTable, "IMEI", imei, borderColor);

        PdfPCell fieldsCell = new PdfPCell(fieldsTable);
        fieldsCell.setBorder(Rectangle.BOX);
        fieldsCell.setBorderColor(borderColor);
        fieldsCell.setPadding(0);
        block.addCell(fieldsCell);

        PdfPCell outer = new PdfPCell(block);
        outer.setBorder(Rectangle.BOTTOM);
        outer.setBorderColor(borderColor);
        outer.setPadding(0);
        return outer;
    }

    /** Category 3: SIM CARD / شريحة */
    private PdfPCell buildSimCategoryBlock(CustodyRecordResponse simItem, Color borderColor, Color darkText) {
        boolean hasSim = simItem != null;
        String simCode = "—";
        String simType = "—";
        String phoneNumber = "—";

        if (hasSim) {
            // Look up the actual AssetItem from inventory to get proper data
            if (assetItemRepository != null && simItem.getReferenceId() != null) {
                try {
                    var assetOpt = assetItemRepository.findById(simItem.getReferenceId());
                    if (assetOpt.isPresent()) {
                        var asset = assetOpt.get();
                        simCode = asset.getSerialCode() != null ? asset.getSerialCode() : "—";
                        for (var f : asset.getFields()) {
                            if ("sim_type".equals(f.getFieldKey()) && f.getFieldValue() != null && !f.getFieldValue().isBlank())
                                simType = f.getFieldValue();
                            if ("phone_number".equals(f.getFieldKey()) && f.getFieldValue() != null && !f.getFieldValue().isBlank())
                                phoneNumber = f.getFieldValue();
                        }
                    }
                } catch (Throwable t) {
                    log.warn("Could not load SIM asset details: {}", t.getMessage());
                }
            }
        }

        PdfPTable block = new PdfPTable(2);
        block.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.setWidthPercentage(100);
        block.setWidths(new float[]{82f, 18f});

        PdfPCell catCell = new PdfPCell(categoryHeaderPhrase("SIM CARD / شريحة", hasSim));
        catCell.setBorder(Rectangle.BOX);
        catCell.setBorderColor(borderColor);
        catCell.setBackgroundColor(new Color(248, 250, 252));
        catCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        catCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        catCell.setPadding(2f);
        catCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.addCell(catCell);

        PdfPTable fieldsTable = new PdfPTable(2);
        fieldsTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        fieldsTable.setWidthPercentage(100);
        fieldsTable.setWidths(new float[]{65f, 35f});

        addTopIdRow(fieldsTable, "رقم الشريحة\nID", simCode, borderColor);
        addCategoryFieldRow(fieldsTable, "نوع الشريحة", simType, borderColor);
        addCategoryFieldRow(fieldsTable, "رقم الاتصال", phoneNumber, borderColor);

        PdfPCell fieldsCell = new PdfPCell(fieldsTable);
        fieldsCell.setBorder(Rectangle.BOX);
        fieldsCell.setBorderColor(borderColor);
        fieldsCell.setPadding(0);
        block.addCell(fieldsCell);

        PdfPCell outer = new PdfPCell(block);
        outer.setBorder(Rectangle.BOTTOM);
        outer.setBorderColor(borderColor);
        outer.setPadding(0);
        return outer;
    }

    /** Category 4: UNIFORMS / الزي */
    private PdfPCell buildUniformCategoryBlock(List<CustodyRecordResponse> items, Color borderColor, Color darkText) {
        boolean hasUniform = items != null && items.stream().anyMatch(i ->
                i.getAssetType() != null && (i.getAssetType().contains("UNIFORM") || i.getAssetType().contains("زي")));

        PdfPTable block = new PdfPTable(2);
        block.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.setWidthPercentage(100);
        block.setWidths(new float[]{82f, 18f});

        PdfPCell catCell = new PdfPCell(categoryHeaderPhrase("UNIFORMS / الزي", hasUniform));
        catCell.setBorder(Rectangle.BOX);
        catCell.setBorderColor(borderColor);
        catCell.setBackgroundColor(new Color(248, 250, 252));
        catCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        catCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        catCell.setPadding(2f);
        catCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.addCell(catCell);

        PdfPTable sub = new PdfPTable(2);
        sub.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        sub.setWidthPercentage(100);
        sub.setWidths(new float[]{50f, 50f});

        addCheckItemRow(sub, "كاب", hasUniform, borderColor);
        addCheckItemRow(sub, "تي شيرت", hasUniform, borderColor);
        addCheckItemRow(sub, "شنطة", hasUniform, borderColor);

        PdfPCell subCell = new PdfPCell(sub);
        subCell.setBorder(Rectangle.BOX);
        subCell.setBorderColor(borderColor);
        subCell.setPadding(0);
        block.addCell(subCell);

        PdfPCell outer = new PdfPCell(block);
        outer.setBorder(Rectangle.BOTTOM);
        outer.setBorderColor(borderColor);
        outer.setPadding(0);
        return outer;
    }

    /** Category 5: OTHER / أخرى */
    private PdfPCell buildOtherCategoryBlock(List<CustodyRecordResponse> items, Color borderColor, Color darkText) {
        boolean hasOther = items != null && items.stream().anyMatch(i ->
                i.getAssetType() != null && !i.getAssetType().equalsIgnoreCase("VEHICLE")
                        && !i.getAssetType().equalsIgnoreCase("PHONE")
                        && !i.getAssetType().equalsIgnoreCase("SIM_CARD")
                        && !i.getAssetType().contains("UNIFORM"));

        PdfPTable block = new PdfPTable(2);
        block.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.setWidthPercentage(100);
        block.setWidths(new float[]{82f, 18f});

        PdfPCell catCell = new PdfPCell(categoryHeaderPhrase("OTHER / أخرى", hasOther));
        catCell.setBorder(Rectangle.BOX);
        catCell.setBorderColor(borderColor);
        catCell.setBackgroundColor(new Color(248, 250, 252));
        catCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        catCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        catCell.setPadding(2f);
        catCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        block.addCell(catCell);

        PdfPTable sub = new PdfPTable(1);
        sub.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        sub.setWidthPercentage(100);

        PdfPCell line1 = new PdfPCell(new Phrase(" ", font(7)));
        line1.setBorder(Rectangle.BOTTOM);
        line1.setBorderColor(borderColor);
        line1.setFixedHeight(16f);
        sub.addCell(line1);

        PdfPCell line2 = new PdfPCell(new Phrase(" ", font(7)));
        line2.setBorder(Rectangle.BOTTOM);
        line2.setBorderColor(borderColor);
        line2.setFixedHeight(16f);
        sub.addCell(line2);

        PdfPCell line3 = new PdfPCell(new Phrase(" ", font(7)));
        line3.setBorder(Rectangle.NO_BORDER);
        line3.setFixedHeight(16f);
        sub.addCell(line3);

        PdfPCell subCell = new PdfPCell(sub);
        subCell.setBorder(Rectangle.BOX);
        subCell.setBorderColor(borderColor);
        subCell.setPadding(0);
        block.addCell(subCell);

        PdfPCell outer = new PdfPCell(block);
        outer.setBorder(Rectangle.NO_BORDER);
        outer.setPadding(0);
        return outer;
    }

    private Phrase categoryHeaderPhrase(String title, boolean checked) {
        Phrase p = new Phrase();
        p.add(new Chunk(shape(title) + "\n\n", font(6.8f, Font.BOLD)));
        p.add(new Chunk(shape(checked ? "[ ✓ ]" : "[   ]"), font(8.5f, Font.BOLD, checked ? new Color(0, 120, 60) : Color.GRAY)));
        return p;
    }

    private void addTopIdRow(PdfPTable table, String label, String val, Color borderColor) {
        PdfPCell lbl = new PdfPCell(new Phrase(shape(label), font(6.5f, Font.BOLD, Color.DARK_GRAY)));
        lbl.setBorder(Rectangle.BOX);
        lbl.setBorderColor(borderColor);
        lbl.setBackgroundColor(new Color(245, 245, 245));
        lbl.setHorizontalAlignment(Element.ALIGN_CENTER);
        lbl.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lbl.setPadding(2.5f);
        lbl.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        PdfPCell v = new PdfPCell(new Phrase(shape(val), font(7.2f, Font.BOLD)));
        v.setBorder(Rectangle.BOX);
        v.setBorderColor(borderColor);
        v.setHorizontalAlignment(Element.ALIGN_CENTER);
        v.setVerticalAlignment(Element.ALIGN_MIDDLE);
        v.setPadding(2.5f);
        v.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        table.addCell(lbl);
        table.addCell(v);
    }

    private void addCategoryFieldRow(PdfPTable table, String label, String val, Color borderColor) {
        PdfPCell lbl = new PdfPCell(new Phrase(shape(label), font(6.6f, Font.BOLD, Color.DARK_GRAY)));
        lbl.setBorder(Rectangle.BOX);
        lbl.setBorderColor(borderColor);
        lbl.setBackgroundColor(new Color(250, 250, 250));
        lbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
        lbl.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lbl.setPadding(2.5f);
        lbl.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        PdfPCell v = new PdfPCell(new Phrase(shape(val), font(7.2f)));
        v.setBorder(Rectangle.BOX);
        v.setBorderColor(borderColor);
        v.setHorizontalAlignment(Element.ALIGN_CENTER);
        v.setVerticalAlignment(Element.ALIGN_MIDDLE);
        v.setPadding(2.5f);
        v.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        table.addCell(lbl);
        table.addCell(v);
    }

    private void addCheckItemRow(PdfPTable table, String label, boolean checked, Color borderColor) {
        PdfPCell cBox = new PdfPCell(new Phrase(shape(checked ? "[ ✓ ]" : "[   ]"), font(7.5f, Font.BOLD, checked ? new Color(0, 120, 60) : Color.GRAY)));
        cBox.setBorder(Rectangle.BOX);
        cBox.setBorderColor(borderColor);
        cBox.setHorizontalAlignment(Element.ALIGN_CENTER);
        cBox.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cBox.setPadding(2.8f);

        PdfPCell lbl = new PdfPCell(new Phrase(shape(label), font(7.2f, Font.BOLD)));
        lbl.setBorder(Rectangle.BOX);
        lbl.setBorderColor(borderColor);
        lbl.setHorizontalAlignment(Element.ALIGN_RIGHT);
        lbl.setVerticalAlignment(Element.ALIGN_MIDDLE);
        lbl.setPadding(2.8f);
        lbl.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);

        table.addCell(lbl);
        table.addCell(cBox);
    }

    // ──────────────────── Page 2 Components ────────────────────

    private void addPage2Header(Document doc, LocalDate date, Color brandGreen, Color darkText) throws DocumentException {
        PdfPTable header = new PdfPTable(3);
        header.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        header.setWidthPercentage(100);
        // RTL: [Cell 0: Right (Company)], [Cell 1: Center (Title)], [Cell 2: Left (Logo)]
        header.setWidths(new float[]{20f, 52f, 28f});

        Phrase comp = new Phrase();
        comp.add(new Chunk(shape("شركة كمال بن خلف السلمي للخدمات اللوجستية") + "\n", font(8.5f, Font.BOLD, Color.BLACK)));
        comp.add(new Chunk("Kamal Logistics Services Co.", font(6.5f, Font.NORMAL, Color.DARK_GRAY)));
        PdfPCell c0 = new PdfPCell(comp);
        c0.setBorder(Rectangle.NO_BORDER);
        c0.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c0.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        header.addCell(c0);

        Phrase t = new Phrase();
        t.add(new Chunk(shape("الشروط والأحكام والتعهد القانوني") + "\n", font(12f, Font.BOLD, Color.BLACK)));
        t.add(new Chunk("TERMS, CONDITIONS & LEGAL UNDERTAKING", font(7.5f, Font.BOLD, Color.BLACK)));
        PdfPCell c1 = new PdfPCell(t);
        c1.setBorder(Rectangle.NO_BORDER);
        c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        c1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c1.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        header.addCell(c1);

        PdfPCell c2 = new PdfPCell();
        c2.setBorder(Rectangle.NO_BORDER);
        c2.setHorizontalAlignment(Element.ALIGN_CENTER);
        c2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        try {
            byte[] logoBytes = new ClassPathResource("branding/logo-ksclc.png").getInputStream().readAllBytes();
            Image img = Image.getInstance(logoBytes);
            img.scaleToFit(70, 30);
            img.setAlignment(Element.ALIGN_CENTER);
            c2.addElement(img);
        } catch (Exception e) {
            c2.addElement(new Phrase("kslsc", font(10, Font.BOLD, Color.BLACK)));
        }
        header.addCell(c2);

        doc.add(header);

        // Divider (Black line)
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell divCell = new PdfPCell(new Phrase(" "));
        divCell.setBorder(Rectangle.BOTTOM);
        divCell.setBorderColor(Color.BLACK);
        divCell.setBorderWidth(1.2f);
        divCell.setFixedHeight(2);
        divider.addCell(divCell);
        doc.add(divider);
    }

    private void addTermsTable(Document doc, Color borderColor, Color darkText) throws DocumentException {
        String[][] terms = {
            {"يتعهد المستلم بالمحافظة على جميع العهد المسلمة إليه والحفاظ عليها في حالة جيدة وصالحة للاستخدام.",
             "The recipient undertakes to maintain all custody items in good and usable condition."},
            {"يتحمل المستلم كامل المسؤولية النظامية والمالية عن أي تلف أو فقدان أو نقص للعهد المسلمة إليه.",
             "The recipient bears full legal and financial responsibility for any damage, loss, or shortage of custody items."},
            {"لا يجوز للمستلم إعارة أو تسليم أو نقل العهد لأي شخص آخر دون إذن وتفويض كتابي مسبق من الإدارة.",
             "The recipient may not lend, transfer, or hand over custody items without prior written management approval."},
            {"تُعاد جميع العهد فور انتهاء الخدمة أو عند طلب الإدارة ذلك وفي حالتها الأصلية المسلمة بها.",
             "All custody items must be returned immediately upon service termination or management request in original condition."},
            {"في حالة تلف أو فقدان أي عهدة، يتحمل المستلم تكلفة الإصلاح أو الاستبدال الكامل كخصم من مستحقاته.",
             "In case of damage or loss, the recipient bears full repair or replacement costs deducted from dues."},
            {"تُستخدم العهد للأغراض المخصصة لها قانوناً لأداء العمل فقط ويُحظر تماماً أي استخدام شخصي.",
             "Custody items must be used strictly for designated job duties and never for any personal purposes."},
            {"يجب الإبلاغ فوراً وخلال 24 ساعة كحد أقصى عن أي عطل أو تلف أو حادث يطرأ على أي من العهد المسلمة.",
             "Any defect, damage, or incident affecting custody items must be reported immediately within 24 hours."},
            {"يحق للشركة استقطاع قيمة أي عهدة مفقودة أو تالفة من راتب أو مستحقات أو مكافآت المستلم مباشرة.",
             "The company reserves the right to deduct the value of lost or damaged items directly from recipient dues."},
            {"المستلم مسؤول عن المحافظة على نظافة السيارة (إن وجدت) ومتابعة الصيانة الدورية في مواعيدها المحددة.",
             "The recipient must maintain vehicle cleanliness (if applicable) and attend scheduled periodic maintenance."},
            {"يُمنع منعاً باتاً استخدام السيارة خارج نطاق العمل التشغيلي المعتمد أو استخدامها لنقل أشخاص غير مصرح بهم.",
             "Using the vehicle outside authorized operational scope or transporting unauthorized persons is strictly prohibited."},
            {"يتحمل المستلم منفرداً كافة المخالفات المرورية والغرامات المسجلة على المركبة خلال فترة استلامه للعهدة.",
             "The recipient is solely liable for all traffic violations and fines recorded on the vehicle during the custody period."},
            {"يجب المحافظة التامة على شريحة الاتصال والهاتف وعدم استخدامهما في أجهزة أو أغراض غير مصرح بها.",
             "The SIM card and phone must be strictly preserved and not used in unauthorized devices or tasks."},
            {"يتحمل المستلم المسؤولية الجنائية والمدنية عن أي استخدام غير مشروع لشريحة الاتصال أو الأجهزة المسلمة له.",
             "The recipient bears full criminal and civil liability for any unlawful use of SIM cards or provided devices."},
            {"يحق للشركة استرداد العهد أو فحصها ومعاينتها دورياً في أي وقت تراه الإدارة دون إبداء أسباب.",
             "The company reserves the right to recall, inspect, or examine custody items at any time without justification."},
            {"يُعد توقيع المستلم على هذا المحضر إقراراً قطعياً بصحة البيانات والموافقة التامة على كافة الشروط والأحكام.",
             "The recipient's signature constitutes irrevocable acknowledgment of accuracy and full agreement to all terms."},
            {"حُرر هذا المحضر من نسختين أصليتين، نُسخة للشركة ونُسخة للمستلم للعمل بموجبها نظاماً.",
             "This document is drawn up in two original counterparts, one for the company and one for the recipient."}
        };

        PdfPTable tcTable = new PdfPTable(3);
        tcTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        tcTable.setWidthPercentage(100);
        // RTL columns: [Cell 0: Right (Seq 5%)], [Cell 1: Center (Arabic 47.5%)], [Cell 2: Left (English 47.5%)]
        // Left-to-Right widths: [English 47.5%], [Arabic 47.5%], [Seq 5%]
        tcTable.setWidths(new float[]{47.5f, 47.5f, 5f});

        // Header - Pure white background, Black border and text
        PdfPCell numHeader = new PdfPCell(new Phrase(shape("م"), font(7.5f, Font.BOLD, Color.BLACK)));
        numHeader.setBackgroundColor(Color.WHITE);
        numHeader.setBorder(Rectangle.BOX);
        numHeader.setBorderColor(Color.BLACK);
        numHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        numHeader.setVerticalAlignment(Element.ALIGN_MIDDLE);
        numHeader.setPadding(3.5f);
        numHeader.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        tcTable.addCell(numHeader);

        PdfPCell arHeader = new PdfPCell(new Phrase(shape("البند والالتزام القانوني (عربي)"), font(7.5f, Font.BOLD, Color.BLACK)));
        arHeader.setBackgroundColor(Color.WHITE);
        arHeader.setBorder(Rectangle.BOX);
        arHeader.setBorderColor(Color.BLACK);
        arHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        arHeader.setVerticalAlignment(Element.ALIGN_MIDDLE);
        arHeader.setPadding(3.5f);
        arHeader.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        tcTable.addCell(arHeader);

        PdfPCell enHeader = new PdfPCell(new Phrase("Term & Condition (English)", font(7.5f, Font.BOLD, Color.BLACK)));
        enHeader.setBackgroundColor(Color.WHITE);
        enHeader.setBorder(Rectangle.BOX);
        enHeader.setBorderColor(Color.BLACK);
        enHeader.setHorizontalAlignment(Element.ALIGN_CENTER);
        enHeader.setVerticalAlignment(Element.ALIGN_MIDDLE);
        enHeader.setPadding(3.5f);
        tcTable.addCell(enHeader);

        for (int i = 0; i < terms.length; i++) {
            PdfPCell numCell = new PdfPCell(new Phrase(String.valueOf(i + 1), font(7.2f, Font.BOLD, Color.BLACK)));
            numCell.setBackgroundColor(Color.WHITE);
            numCell.setBorder(Rectangle.BOX);
            numCell.setBorderColor(Color.BLACK);
            numCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            numCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            numCell.setPadding(3.5f);
            tcTable.addCell(numCell);

            PdfPCell arCell = new PdfPCell(new Phrase(shape(terms[i][0]), font(7.2f, Font.NORMAL, Color.BLACK)));
            arCell.setBackgroundColor(Color.WHITE);
            arCell.setBorder(Rectangle.BOX);
            arCell.setBorderColor(Color.BLACK);
            arCell.setPadding(3.5f);
            arCell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            arCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            tcTable.addCell(arCell);

            PdfPCell enCell = new PdfPCell(new Phrase(terms[i][1], font(6.5f, Font.NORMAL, Color.BLACK)));
            enCell.setBackgroundColor(Color.WHITE);
            enCell.setBorder(Rectangle.BOX);
            enCell.setBorderColor(Color.BLACK);
            enCell.setPadding(3.5f);
            enCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            enCell.setHorizontalAlignment(Element.ALIGN_LEFT);
            tcTable.addCell(enCell);
        }

        doc.add(tcTable);
    }

    private void addLegalUndertaking(Document doc, Color borderColor, Color darkText) throws DocumentException {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);

        Phrase p = new Phrase();
        p.add(new Chunk(shape("إقرار وتعهد المستلم: ") + "\n", font(8.2f, Font.BOLD, Color.BLACK)));
        p.add(new Chunk(shape("أقر أنا الموقع أدناه بصفتي مستلماً للعهد الموضحة بالصفحة الأولى من هذا المحضر، بأنني عاينتها واستلمتها كاملة وسليمة وبحالة جيدة صالحة للعمل، وأتعهد بالمحافظة التامة عليها واستخدامها في مهام العمل التشغيلي المخصصة فقط، وأتحمل كامل المسؤولية النظامية والمالية في حال فقدانها أو تلفها، كما أقر بموافقتي والتزامي التام بجميع الشروط والأحكام المدونة أعلاه وبكافة لوائح وتعليمات الشركة."),
                font(7.2f, Font.NORMAL, Color.BLACK)));

        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.BLACK);
        cell.setBackgroundColor(Color.WHITE); // Pure white background for printing
        cell.setPadding(6f);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        box.addCell(cell);

        doc.add(box);
    }

    private void addSignaturesTable(Document doc, String captainName, Color borderColor, Color darkText) throws DocumentException {
        PdfPTable signTable = new PdfPTable(3);
        signTable.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signTable.setWidthPercentage(100);
        signTable.setWidths(new float[]{33.3f, 33.4f, 33.3f});

        // Right (Cell 0): المُسلِم (Deliverer)
        Phrase p1 = new Phrase();
        p1.add(new Chunk(shape("المُسلِم (مسؤول العهد / أمين المستودع)") + "\n", font(8f, Font.BOLD, Color.BLACK)));
        p1.add(new Chunk(shape("الاسم: ___________________________") + "\n\n", font(7.5f, Font.NORMAL, Color.BLACK)));
        p1.add(new Chunk(shape("التوقيع: ________________________") + "\n\n", font(7.5f, Font.NORMAL, Color.BLACK)));
        p1.add(new Chunk(shape("التاريخ: ___ / ___ / 2026 م"), font(7.5f, Font.NORMAL, Color.BLACK)));
        PdfPCell c1 = new PdfPCell(p1);
        c1.setBorder(Rectangle.BOX);
        c1.setBorderColor(Color.BLACK);
        c1.setBackgroundColor(Color.WHITE);
        c1.setPadding(6f);
        c1.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signTable.addCell(c1);

        // Center (Cell 1): المُستلم (Recipient)
        Phrase p2 = new Phrase();
        p2.add(new Chunk(shape("المُستلِم (الكابتن / المستلم)") + "\n", font(8f, Font.BOLD, Color.BLACK)));
        p2.add(new Chunk(shape("الاسم: " + (captainName != null ? captainName : "_______________")) + "\n\n", font(7.8f, Font.BOLD, Color.BLACK)));
        p2.add(new Chunk(shape("التوقيع: ________________________") + "\n\n", font(7.5f, Font.NORMAL, Color.BLACK)));
        p2.add(new Chunk(shape("التاريخ: ___ / ___ / 2026 م"), font(7.5f, Font.NORMAL, Color.BLACK)));
        PdfPCell c2 = new PdfPCell(p2);
        c2.setBorder(Rectangle.BOX);
        c2.setBorderColor(Color.BLACK);
        c2.setBackgroundColor(Color.WHITE);
        c2.setPadding(6f);
        c2.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signTable.addCell(c2);

        // Left (Cell 2): الاعتماد (Operations)
        Phrase p3 = new Phrase();
        p3.add(new Chunk(shape("الاعتماد (إدارة العمليات والتشغيل)") + "\n", font(8f, Font.BOLD, Color.BLACK)));
        p3.add(new Chunk(shape("الاسم: ___________________________") + "\n\n", font(7.5f, Font.NORMAL, Color.BLACK)));
        p3.add(new Chunk(shape("الختم / التوقيع: ________________") + "\n\n", font(7.5f, Font.NORMAL, Color.BLACK)));
        p3.add(new Chunk(shape("التاريخ: ___ / ___ / 2026 م"), font(7.5f, Font.NORMAL, Color.BLACK)));
        PdfPCell c3 = new PdfPCell(p3);
        c3.setBorder(Rectangle.BOX);
        c3.setBorderColor(Color.BLACK);
        c3.setBackgroundColor(Color.WHITE);
        c3.setPadding(6f);
        c3.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        signTable.addCell(c3);

        doc.add(signTable);
    }

    private void addPage2Footer(Document doc) throws DocumentException {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(
                shape("رقم النموذج: HMRLS - HR - 0010 — نموذج استلام وتسليم عهد — شركة كمال بن خلف السلمي للخدمات اللوجستية"),
                font(6.5f, Font.ITALIC, Color.BLACK)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        cell.setPadding(2);
        t.addCell(cell);
        doc.add(t);
    }

    // ──────────────────── Utility ────────────────────

    private Paragraph spacer(float points) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingBefore(points);
        return p;
    }
}
