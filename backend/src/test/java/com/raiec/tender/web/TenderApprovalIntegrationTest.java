package com.raiec.tender.web;

import com.raiec.lar.repository.LarRecordRepository;
import com.raiec.tender.service.TenderService;
import com.raiec.tender.web.dto.ApprovalResponse;
import com.raiec.tender.web.dto.TenderSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies the officer-approval flow: approving a tender copies its Non-Scheduled items
 * into the LAR dataset and marks the tender APPROVED. @Transactional rolls back afterwards.
 */
@SpringBootTest
@Transactional
class TenderApprovalIntegrationTest {

    private static final Path PDF = Paths.get("..", "sample-tenders", "viewNitPdf_5207353.pdf");

    @Autowired
    private TenderService tenderService;

    @Autowired
    private LarRecordRepository larRecordRepository;

    @Test
    void approveCopiesNonScheduledItemsIntoLar() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");
        byte[] bytes = Files.readAllBytes(PDF);

        TenderSummaryResponse uploaded = tenderService.ingestPdf(bytes, "232-25-26.pdf");
        assertEquals(0, larRecordRepository.count(), "LAR should start empty");

        ApprovalResponse approved = tenderService.approve(uploaded.id());
        assertEquals("APPROVED", approved.status());
        // Tender 232-25-26 has 6 distinct NS items -> 6 LAR records.
        assertEquals(6, approved.larAdded());
        assertEquals(6, larRecordRepository.count(), "all 6 NS items should be in LAR");

        // Re-approving must not duplicate LAR rows.
        assertThrows(IllegalArgumentException.class, () -> tenderService.approve(uploaded.id()));
        assertEquals(6, larRecordRepository.count());
    }

    @Test
    void approveMixedNsTender160IntoLar() throws Exception {
        Path pdf = Paths.get("..", "sample-tenders", "viewNitPdf_5204559.pdf");
        assumeTrue(Files.exists(pdf), "160 sample PDF not present locally");
        byte[] bytes = Files.readAllBytes(pdf);

        TenderSummaryResponse uploaded = tenderService.ingestPdf(bytes, "160-2025.pdf");
        ApprovalResponse approved = tenderService.approve(uploaded.id());

        assertEquals("APPROVED", approved.status());
        // Granular NS (inline rows + each priced breakup row) exceeds the 8 entry-level items.
        assertTrue(approved.larAdded() > 8);
        assertEquals(approved.larAdded(), (int) larRecordRepository.count());
    }
}
