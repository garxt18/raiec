package com.raiec.tender.service;

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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * "Request more info" is a hold, not a verdict: the officer is asking the filing department
 * for clarification and has not yet decided. It must therefore leave the tender open, and it
 * must not be able to reopen something already approved or rejected.
 */
@SpringBootTest
@Transactional
class RequestInfoTest {

    private static final Path PDF = Paths.get("..", "sample-tenders", "viewNitPdf_5207353.pdf");

    @Autowired
    private TenderService tenderService;

    private Long uploadTender() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");
        TenderSummaryResponse s = tenderService.ingestPdf(Files.readAllBytes(PDF), "232-25-26.pdf");
        return s.id();
    }

    @Test
    void movesTenderToInfoRequestedAndKeepsTheRemark() throws Exception {
        Long id = uploadTender();
        var res = tenderService.requestInfo(id, "  Please attach the market quotations for NS items.  ");

        assertEquals("INFO_REQUESTED", res.status());
        var detail = tenderService.getDetail(id);
        assertEquals("INFO_REQUESTED", detail.status());
        // trimmed, so stray whitespace from a textarea does not reach the record
        assertEquals("Please attach the market quotations for NS items.", detail.officerRemark());
    }

    @Test
    void aTenderOnHoldCanStillBeDecided() throws Exception {
        Long id = uploadTender();
        tenderService.requestInfo(id, "need clarification");

        // The hold must not be a dead end.
        var approved = tenderService.approve(id);
        assertEquals("APPROVED", approved.status());
    }

    @Test
    void anAlreadyApprovedTenderCannotBeReopenedForInformation() throws Exception {
        Long id = uploadTender();
        tenderService.approve(id);

        var e = assertThrows(IllegalArgumentException.class,
                () -> tenderService.requestInfo(id, "too late"));
        assertEquals(true, e.getMessage().contains("already decided"));
    }

    @Test
    void aBlankRemarkLeavesTheExistingNoteAlone() throws Exception {
        Long id = uploadTender();
        tenderService.requestInfo(id, "first question");
        tenderService.requestInfo(id, "   ");
        assertEquals("first question", tenderService.getDetail(id).officerRemark());
    }
}
