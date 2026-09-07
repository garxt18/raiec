package com.raiec.ratematch.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A tenderer quotes one common percentage against the schedule - "AT Par", "(+) 8.50" or
 * "(-) 3.20" - and that percentage is loaded onto the rate-book rate to give the rate actually
 * payable. Variance must be measured against that escalated rate, otherwise every item in an
 * escalated schedule reads as over-quoted by exactly the escalation percentage.
 */
class EscalationTest {

    private static void assertRate(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    @Test
    void positiveEscalationRaisesTheAcceptableRate() {
        // 1000.00 quoted at (+) 8.50  ->  1085.00 is acceptable
        assertRate("1085.00", RateMatchService.applyEscalation(
                new BigDecimal("1000.00"), new BigDecimal("8.50")));
    }

    @Test
    void negativeEscalationLowersTheAcceptableRate() {
        // 1000.00 quoted at (-) 3.20  ->  968.00 is acceptable
        assertRate("968.00", RateMatchService.applyEscalation(
                new BigDecimal("1000.00"), new BigDecimal("-3.20")));
    }

    @Test
    void atParLeavesTheBookRateUnchanged() {
        // "AT Par" is parsed as 0%, meaning exactly the schedule rate.
        assertRate("7780.30", RateMatchService.applyEscalation(
                new BigDecimal("7780.30"), BigDecimal.ZERO));
    }

    @Test
    void missingEscalationLeavesTheBookRateUnchanged() {
        assertRate("7780.30", RateMatchService.applyEscalation(
                new BigDecimal("7780.30"), null));
    }

    @Test
    void noReferenceRateStaysNull() {
        assertNull(RateMatchService.applyEscalation(null, new BigDecimal("8.50")));
    }

    @Test
    void anItemAtTheEscalatedRateIsNotOverQuoted() {
        // Regression: before escalation was applied, a rate quoted exactly at book + 8.5%
        // was reported as +8.5% variance and flagged WARN. It should now be 0%.
        BigDecimal book = new BigDecimal("1000.00");
        BigDecimal quoted = new BigDecimal("1085.00");
        BigDecimal effective = RateMatchService.applyEscalation(book, new BigDecimal("8.50"));
        assertEquals(0, quoted.compareTo(effective),
                "a rate quoted exactly at the escalated schedule rate must show no variance");
    }
}
