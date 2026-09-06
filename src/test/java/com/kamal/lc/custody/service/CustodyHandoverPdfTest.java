package com.kamal.lc.custody.service;

import com.kamal.lc.custody.dto.CaptainCustodySummary;
import com.kamal.lc.custody.dto.CustodyRecordResponse;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.FileOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CustodyHandoverPdfTest {

    @Test
    void testGeneratePdf() throws Exception {
        CustodyService custodyService = Mockito.mock(CustodyService.class);
        CustodyHandoverPdfService pdfService = new CustodyHandoverPdfService(custodyService);

        UUID captainId = UUID.randomUUID();
        CustodyRecordResponse item1 = CustodyRecordResponse.builder()
                .assetType("PHONE")
                .assetTypeLabelAr("جهاز هاتف")
                .assetLabel("Samsung Galaxy A15 - S/N: 89965412")
                .assignedAt("2026-05-10T10:00:00")
                .assignedBy("إدارة العمليات")
                .build();

        CustodyRecordResponse item2 = CustodyRecordResponse.builder()
                .assetType("SIM_CARD")
                .assetTypeLabelAr("شريحة اتصال")
                .assetLabel("STC 5G - 0501234567")
                .assignedAt("2026-05-10T10:00:00")
                .assignedBy("إدارة العمليات")
                .build();

        CaptainCustodySummary summary = CaptainCustodySummary.builder()
                .captainId(captainId)
                .captainName("محمد أحمد إبراهيم")
                .captainCode("KSC-1052")
                .activeCount(2)
                .phoneCount(1)
                .simCount(1)
                .activeItems(List.of(item1, item2))
                .build();

        when(custodyService.getCaptainSummaries()).thenReturn(List.of(summary));

        byte[] pdf = pdfService.buildHandoverPdf(captainId);
        assertThat(pdf).isNotNull().isNotEmpty();

        // Write out to temp file to inspect
        try (FileOutputStream fos = new FileOutputStream("target/test-handover.pdf")) {
            fos.write(pdf);
        }

        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(2);
        reader.close();
    }

    @Test
    void testGeneratePdfWithVehicleGeneratesAnnex() throws Exception {
        CustodyService custodyService = Mockito.mock(CustodyService.class);
        CustodyHandoverPdfService pdfService = new CustodyHandoverPdfService(custodyService);

        UUID captainId = UUID.randomUUID();
        CustodyRecordResponse vehicle = CustodyRecordResponse.builder()
                .assetType("VEHICLE")
                .assetTypeLabelAr("مركبة")
                .assetLabel("تويوتا يارس 2023 - لوحة: أ ص م 5374")
                .assignedAt("2026-05-10T10:00:00")
                .assignedBy("إدارة العمليات")
                .build();

        CaptainCustodySummary summary = CaptainCustodySummary.builder()
                .captainId(captainId)
                .captainName("علاء الدين عمر")
                .captainCode("KSC0025")
                .activeCount(1)
                .vehicleCount(1)
                .activeItems(List.of(vehicle))
                .build();

        when(custodyService.getCaptainSummaries()).thenReturn(List.of(summary));

        byte[] pdf = pdfService.buildHandoverPdf(captainId);
        assertThat(pdf).isNotNull().isNotEmpty();

        PdfReader reader = new PdfReader(pdf);
        // Page 1 is Form with car diagram, Page 2 is Terms and Signatures -> exactly 2 pages
        assertThat(reader.getNumberOfPages()).isEqualTo(2);
        reader.close();
    }
}
