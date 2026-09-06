package com.kamal.lc.custody.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RealCustodyPdfGenerationTest {

    @Autowired
    private CustodyService custodyService;

    @Autowired
    private CustodyHandoverPdfService pdfService;

    @Test
    void testRealDatabaseCaptains() throws Exception {
        var summaries = custodyService.getCaptainSummaries();
        assertThat(summaries).isNotEmpty();

        for (var s : summaries) {
            System.out.println("Testing PDF for captain: " + s.getCaptainName() + " (" + s.getCaptainCode() + ")");
            byte[] pdf = pdfService.buildHandoverPdf(s.getCaptainId());
            assertThat(pdf).isNotNull().isNotEmpty();

            try (FileOutputStream fos = new FileOutputStream("target/test-real-" + s.getCaptainCode() + ".pdf")) {
                fos.write(pdf);
            }

            com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf);
            System.out.println("Pages for " + s.getCaptainCode() + ": " + reader.getNumberOfPages());
            assertThat(reader.getNumberOfPages()).isEqualTo(2);
            reader.close();
        }
    }
}
