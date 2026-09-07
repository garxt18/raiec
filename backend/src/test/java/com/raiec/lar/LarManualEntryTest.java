package com.raiec.lar;

import com.raiec.lar.service.LarService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hand-entered rate becomes the benchmark future tenders are vetted against, so it has
 * to obey the same rule as one earned through an approval: lowest accepted rate wins.
 */
@SpringBootTest
@Transactional
class LarManualEntryTest {

    @Autowired
    private LarService larService;

    private static final String DESC = "Supplying and fixing precast concrete kerb stones";

    @Test
    void addsANewRateAndAssignsALarCode() {
        var r = larService.addManual(DESC, "metre", new BigDecimal("450.00"),
                new BigDecimal("100"), "Bikaner", "ADEN/HQ", null, LocalDate.now().minusDays(3));
        assertEquals(0, new BigDecimal("450.00").compareTo(r.rate()));
        assertTrue(r.larCode().startsWith("LAR-"), "expected a generated LAR code, got " + r.larCode());
    }

    @Test
    void aLowerRateReplacesTheExistingBenchmark() {
        larService.addManual(DESC, "metre", new BigDecimal("450.00"), null, null, null, null, null);
        var better = larService.addManual(DESC, "metre", new BigDecimal("399.00"), null, null, null, null, null);
        assertEquals(0, new BigDecimal("399.00").compareTo(better.rate()));
    }

    @Test
    void aHigherRateIsRefused() {
        larService.addManual(DESC, "metre", new BigDecimal("399.00"), null, null, null, null, null);
        // Allowing this would let anyone raise the bar a tender is measured against,
        // which defeats the purpose of holding a lowest accepted rate.
        var e = assertThrows(IllegalArgumentException.class, () ->
                larService.addManual(DESC, "metre", new BigDecimal("500.00"), null, null, null, null, null));
        assertTrue(e.getMessage().contains("lowest accepted rate"), e.getMessage());
    }

    @Test
    void anEqualRateIsAlsoRefused() {
        larService.addManual(DESC, "metre", new BigDecimal("399.00"), null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () ->
                larService.addManual(DESC, "metre", new BigDecimal("399.00"), null, null, null, null, null));
    }

    @Test
    void rateMustBePositive() {
        assertThrows(IllegalArgumentException.class, () ->
                larService.addManual(DESC, "metre", BigDecimal.ZERO, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                larService.addManual(DESC, "metre", new BigDecimal("-5"), null, null, null, null, null));
    }

    @Test
    void descriptionMustBeUsable() {
        // The description is what future tenders are matched against, so "x" is useless.
        assertThrows(IllegalArgumentException.class, () ->
                larService.addManual("x", "metre", new BigDecimal("10"), null, null, null, null, null));
    }

    @Test
    void aRateCannotBeAcceptedInTheFuture() {
        assertThrows(IllegalArgumentException.class, () ->
                larService.addManual(DESC, "metre", new BigDecimal("10"), null, null, null, null,
                        LocalDate.now().plusDays(1)));
    }
}
