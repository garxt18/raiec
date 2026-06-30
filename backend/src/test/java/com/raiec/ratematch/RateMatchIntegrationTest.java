package com.raiec.ratematch;

import com.raiec.ratematch.service.RateMatchService;
import com.raiec.ratematch.web.dto.RateMatchResponse;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@Transactional
class RateMatchIntegrationTest {

    private static final Path PDF = Paths.get("..", "sample-tenders", "viewNitPdf_5207353.pdf");

    @Autowired
    private TenderService tenderService;
    @Autowired
    private RateMatchService rateMatchService;

    @Test
    void selfRecordsAreNotUsedAsReference() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");
        TenderSummaryResponse uploaded = tenderService.ingestPdf(Files.readAllBytes(PDF), "232-25-26.pdf");
        tenderService.approve(uploaded.id()); // LAR now holds THIS tender's NS items

        RateMatchResponse r = rateMatchService.evaluate(uploaded.id());

        // The tender's own LAR records are excluded, and DSR/IRUSSOR books aren't loaded,
        // so nothing should match (proves we don't self-reference).
        assertEquals(0, r.matched());
        assertTrue(r.noReference() > 0, "items should be NO_REFERENCE");
        assertTrue(r.totalItems() > 6);
    }
}
