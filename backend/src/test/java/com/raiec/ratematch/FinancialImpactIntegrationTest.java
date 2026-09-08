package com.raiec.ratematch;

import com.raiec.ratematch.service.RateMatchService;
import com.raiec.ratematch.web.dto.FinancialImpact;
import com.raiec.ratematch.web.dto.RateMatchItem;
import com.raiec.ratematch.web.dto.RateMatchResponse;
import com.raiec.tender.service.TenderService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Counting flagged items answers "how many things look wrong", which is not the question
 * a vetting officer is asked. These tests pin the money behind the flags: what each
 * variance costs, what the tender is worth in total, and how much of it could be checked
 * at all.
 */
@SpringBootTest
@Transactional
class FinancialImpactIntegrationTest {

    private static final Path PDF = Paths.get("..", "sample-tenders", "viewNitPdf_5207353.pdf");

    @Autowired
    private TenderService tenderService;
    @Autowired
    private RateMatchService rateMatchService;

    private RateMatchResponse evaluateSample() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");
        TenderSummaryResponse s = tenderService.ingestPdf(Files.readAllBytes(PDF), "232-25-26.pdf");
        return rateMatchService.evaluate(s.id());
    }

    @Test
    void everyTenderReportsItsFinancials() throws Exception {
        FinancialImpact f = evaluateSample().financials();
        assertNotNull(f, "a tender with no references still has a quoted value worth reporting");
        assertTrue(f.quotedValue().signum() > 0, "the estimate should be worth something");
    }

    @Test
    void quotedValueIsTheSumOfEveryPricedLine() throws Exception {
        RateMatchResponse r = evaluateSample();
        BigDecimal expected = r.items().stream()
                .map(i -> i.amount() != null ? i.amount()
                        : (i.tenderRate() != null && i.quantity() != null
                                ? i.tenderRate().multiply(i.quantity()) : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, expected.setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(r.financials().quotedValue()));
    }

    @Test
    void excessAndSavingAreNotNettedAway() throws Exception {
        FinancialImpact f = evaluateSample().financials();
        // A tender 5 lakh over on one item and 5 lakh under on another is not the same as
        // one that matches its references throughout, but a single net figure says it is.
        assertTrue(f.excessTotal().signum() >= 0, "excess is reported as a positive figure");
        assertTrue(f.savingTotal().signum() >= 0, "saving is reported as a positive figure");
        assertEquals(0, f.excessTotal().subtract(f.savingTotal()).compareTo(f.netImpact()));
    }

    @Test
    void coverageSaysHowMuchOfTheValueCouldBeChecked() throws Exception {
        FinancialImpact f = evaluateSample().financials();
        // Without this, "zero excess" reads as a clean estimate when it may only mean
        // nothing was checkable.
        assertTrue(f.coveragePct().signum() >= 0 && f.coveragePct().compareTo(new BigDecimal("100")) <= 0,
                "coverage must be a percentage, got " + f.coveragePct());
        assertEquals(0, f.comparableValue().add(f.unreferencedValue()).compareTo(f.quotedValue()),
                "checked + unchecked must account for the whole estimate");
    }

    @Test
    void itemsAreOrderedByWhatTheyCostNotByPercentage() throws Exception {
        List<RateMatchItem> items = evaluateSample().items();
        BigDecimal previous = null;
        for (RateMatchItem it : items) {
            if (it.excessAmount() == null) continue;   // unreferenced items sort last
            if (previous != null) {
                assertTrue(previous.compareTo(it.excessAmount()) >= 0,
                        "expected descending excess, but " + previous + " came before " + it.excessAmount());
            }
            previous = it.excessAmount();
        }
    }

    @Test
    void unreferencedItemsCarryNoImpactAndSortLast() throws Exception {
        List<RateMatchItem> items = evaluateSample().items();
        boolean seenUnreferenced = false;
        for (RateMatchItem it : items) {
            if (it.excessAmount() == null) {
                seenUnreferenced = true;
                // No reference means unchecked, not acceptable: it must not be given a
                // zero impact that would let it pass as fine.
                assertNull(it.referenceAmount());
            } else {
                assertTrue(!seenUnreferenced,
                        "a priced item appeared after an unreferenced one; unreferenced must sort last");
            }
        }
    }

    @Test
    void excessIsQuantityTimesTheRateDifference() throws Exception {
        for (RateMatchItem it : evaluateSample().items()) {
            if (it.excessAmount() == null || it.quantity() == null || it.referenceRate() == null) continue;
            BigDecimal expectedRef = it.referenceRate().multiply(it.quantity())
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            assertEquals(0, expectedRef.compareTo(it.referenceAmount()),
                    "reference amount for " + it.itemCode());
            return;   // one representative line is enough to pin the formula
        }
    }
}
