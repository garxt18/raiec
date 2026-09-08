package com.raiec.tender.service;

import com.raiec.tender.web.dto.TenderEventResponse;
import com.raiec.tender.web.dto.TenderSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The audit trail exists to answer "who decided this, and when" after the fact. These tests
 * pin the properties that make it able to: that decisions are recorded at all, that they
 * carry an actor and a time, that they stay in order, and that routine re-runs do not bury
 * them under repetition.
 */
@SpringBootTest
@Transactional
class TenderEventTrailTest {

    private static final Path PDF = Paths.get("..", "sample-tenders", "viewNitPdf_5207353.pdf");

    @Autowired
    private TenderService tenderService;
    @Autowired
    private TenderEventService events;

    private Long uploadTender() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");
        TenderSummaryResponse s = tenderService.ingestPdf(Files.readAllBytes(PDF), "232-25-26.pdf");
        return s.id();
    }

    private static List<String> types(List<TenderEventResponse> trail) {
        return trail.stream().map(TenderEventResponse::type).toList();
    }

    @Test
    void uploadingRecordsBothArrivalAndExtraction() throws Exception {
        List<TenderEventResponse> trail = events.forTender(uploadTender());
        assertEquals(List.of(TenderEventService.UPLOADED, TenderEventService.EXTRACTED), types(trail));
        assertTrue(trail.get(0).detail().contains("232-25-26.pdf"), "the filename is the provenance");
    }

    @Test
    void everyEntryCarriesAnActorAndATime() throws Exception {
        for (TenderEventResponse e : events.forTender(uploadTender())) {
            assertNotNull(e.at(), "an event without a time answers no question");
            assertNotNull(e.actor(), "an event without an actor answers the wrong question");
            assertTrue(!e.actor().isBlank());
        }
    }

    @Test
    void approvalIsRecordedAfterTheStepsThatLedToIt() throws Exception {
        Long id = uploadTender();
        tenderService.approve(id);

        List<String> t = types(events.forTender(id));
        assertTrue(t.contains(TenderEventService.APPROVED), "an approval must leave a trace");
        assertEquals(TenderEventService.APPROVED, t.get(t.size() - 1),
                "the decision comes last, after the work that informed it");
    }

    @Test
    void rejectionAndClarificationAreRecordedWithTheirReason() throws Exception {
        Long id = uploadTender();
        tenderService.requestInfo(id, "Attach market quotations for the NS items.");

        TenderEventResponse last = events.forTender(id).stream()
                .reduce((a, b) -> b).orElseThrow();
        assertEquals(TenderEventService.INFO_REQUESTED, last.type());
        assertTrue(last.detail().contains("market quotations"),
                "the remark is the point of the entry: " + last.detail());
    }

    @Test
    void reRunningAStepDoesNotRepeatItsEntry() throws Exception {
        Long id = uploadTender();
        String detail = "Rate match completed — 2 item(s) above tolerance";
        events.recordOnce(id, TenderEventService.RATE_MATCHED, detail, new BigDecimal("1000"));
        events.recordOnce(id, TenderEventService.RATE_MATCHED, detail, new BigDecimal("1000"));
        events.recordOnce(id, TenderEventService.RATE_MATCHED, detail, new BigDecimal("1000"));

        long count = types(events.forTender(id)).stream()
                .filter(TenderEventService.RATE_MATCHED::equals).count();
        assertEquals(1, count, "opening the page three times is not three events");
    }

    @Test
    void anotherStepRunningInBetweenDoesNotReopenTheDuplicate() throws Exception {
        Long id = uploadTender();
        String detail = "Rate match completed — 2 item(s) above tolerance";

        // Walking forward and back interleaves the steps. If duplicate detection only
        // looked at the last event, this middle entry would let the unchanged rate-match
        // result back in, and every trip through the workflow would add a line.
        events.recordOnce(id, TenderEventService.RATE_MATCHED, detail, null);
        events.recordOnce(id, TenderEventService.AI_ANALYSED, "AI analysis completed", null);
        events.recordOnce(id, TenderEventService.RATE_MATCHED, detail, null);

        long count = types(events.forTender(id)).stream()
                .filter(TenderEventService.RATE_MATCHED::equals).count();
        assertEquals(1, count, "an unchanged result is still the same result");
    }

    @Test
    void aChangedResultIsStillRecorded() throws Exception {
        Long id = uploadTender();
        events.recordOnce(id, TenderEventService.RATE_MATCHED, "Rate match completed — 2 over", null);
        events.recordOnce(id, TenderEventService.RATE_MATCHED, "Rate match completed — 5 over", null);

        long count = types(events.forTender(id)).stream()
                .filter(TenderEventService.RATE_MATCHED::equals).count();
        // Deduplication must not hide the case that matters: the same step producing a
        // different answer than it did before.
        assertEquals(2, count, "a different outcome is a different event");
    }

    @Test
    void theExcessAtTheTimeOfTheDecisionIsKeptOnTheEntry() throws Exception {
        Long id = uploadTender();
        events.recordOnce(id, TenderEventService.RATE_MATCHED, "Rate match completed", new BigDecimal("450000.00"));

        TenderEventResponse e = events.forTender(id).stream()
                .filter(x -> TenderEventService.RATE_MATCHED.equals(x.type()))
                .findFirst().orElseThrow();
        // Rate books change. What the officer was shown must not.
        assertEquals(0, new BigDecimal("450000.00").compareTo(e.excessAtEvent()));
    }
}
