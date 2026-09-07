package com.raiec.ratematch;

import com.raiec.lar.entity.LarRecord;
import com.raiec.lar.repository.LarRecordRepository;
import com.raiec.ratematch.service.RateMatchService;
import com.raiec.ratematch.web.dto.RateMatchItem;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The LAR rule is "latest AND least": a rate stays a valid benchmark for 12 months. Older rates
 * are still used - a stale benchmark beats no benchmark - but must be flagged so the vetting
 * officer knows the comparison may be out of date.
 */
@SpringBootTest
@Transactional
class LarRecencyIntegrationTest {

    private static final Path PDF = Paths.get("..", "sample-tenders", "viewNitPdf_5207353.pdf");

    @Autowired
    private TenderService tenderService;
    @Autowired
    private RateMatchService rateMatchService;
    @Autowired
    private LarRecordRepository larRecordRepository;

    /**
     * Approves the tender to populate LAR from its own NS items, then re-points those records at
     * a different source tender (so self-exclusion no longer hides them) and back-dates them by
     * the given number of months. Re-evaluating the tender then matches against those records.
     */
    private RateMatchResponse evaluateAgainstLarAgedBy(long months) throws Exception {
        TenderSummaryResponse uploaded = tenderService.ingestPdf(Files.readAllBytes(PDF), "232-25-26.pdf");
        tenderService.approve(uploaded.id());

        List<LarRecord> records = larRecordRepository.findAll();
        assumeTrue(!records.isEmpty(), "tender produced no NS items to build LAR from");
        for (LarRecord r : records) {
            r.setSourceTenderNo("OTHER-TENDER-001");
            r.setApprovedOn(LocalDate.now().minusMonths(months));
        }
        larRecordRepository.saveAll(records);

        return rateMatchService.evaluate(uploaded.id());
    }

    private static List<RateMatchItem> matchedNsItems(RateMatchResponse r) {
        return r.items().stream()
                .filter(it -> "NS".equals(it.source()) && it.referenceRate() != null)
                .toList();
    }

    @Test
    void recentLarRecordsAreNotFlaggedStale() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");
        RateMatchResponse r = evaluateAgainstLarAgedBy(3);

        List<RateMatchItem> ns = matchedNsItems(r);
        assumeTrue(!ns.isEmpty(), "no NS item matched the LAR dataset");
        assertTrue(ns.stream().noneMatch(RateMatchItem::referenceStale),
                "a 3-month-old LAR record is within the 12-month window and must not be stale");
    }

    @Test
    void larRecordsOlderThanTwelveMonthsAreUsedButFlagged() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");
        RateMatchResponse r = evaluateAgainstLarAgedBy(18);

        List<RateMatchItem> ns = matchedNsItems(r);
        assumeTrue(!ns.isEmpty(), "no NS item matched the LAR dataset");

        // Still used as a benchmark ...
        assertFalse(ns.isEmpty(), "an out-of-date LAR rate is still better than no reference");
        // ... but every one of them is flagged.
        assertTrue(ns.stream().allMatch(RateMatchItem::referenceStale),
                "an 18-month-old LAR record must be flagged stale");
        assertTrue(ns.stream().allMatch(it -> it.referenceSource().contains("older than 12 months")),
                "the stale reason must be visible to the officer in the source label");
    }
}
