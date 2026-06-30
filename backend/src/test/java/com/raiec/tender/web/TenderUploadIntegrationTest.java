package com.raiec.tender.web;

import com.raiec.tender.repository.TenderRepository;
import com.raiec.tender.service.TenderService;
import com.raiec.tender.web.dto.TenderSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end ingest test through the real Spring beans + database:
 * parse the sample PDF, persist it, and check the summary + repository.
 * @Transactional rolls the inserted rows back after the test.
 */
@SpringBootTest
@Transactional
class TenderUploadIntegrationTest {

    private static final Path PDF = Paths.get("..", "sample-tenders", "viewNitPdf_5207353.pdf");

    @Autowired
    private TenderService tenderService;

    @Autowired
    private TenderRepository tenderRepository;

    @Test
    void ingestParsesAndPersistsTender() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");
        byte[] bytes = Files.readAllBytes(PDF);

        TenderSummaryResponse res = tenderService.ingestPdf(bytes, "232-25-26.pdf");

        assertNotNull(res.id(), "saved tender should have an id");
        assertEquals("232-25-26", res.tenderNo());
        assertEquals("Bikaner", res.division());
        assertEquals(3, res.scheduleCount());
        assertTrue(res.totalBreakupItems() > 0, "should have parsed breakup items");
        assertTrue(tenderRepository.existsByTenderNo("232-25-26"), "tender should be persisted");
    }
}
