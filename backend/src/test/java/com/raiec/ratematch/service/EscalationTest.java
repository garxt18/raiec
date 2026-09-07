package com.raiec.ratematch.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Escalation applies to the SCHEDULE TOTAL, not to individual unit rates.
 *
 * <p>In an IREPS tender the item breakup's unit rates sum to the schedule's Basic Value, and the
 * quoted percentage ("AT Par", "(+) 8.50", "(-) 38.00") is then applied once to that total to give
 * the Amount payable. Verified against tender 232-25-26, Schedule A:
 *
 * <pre>
 *   Basic Value 2,867,651.45  ×  (1 - 38%)  =  Amount 1,777,943.90
 * </pre>
 *
 * <p>Because the breakup unit rate and the rate-book rate are BOTH pre-escalation figures, per-item
 * variance compares them directly. Applying the percentage to only the reference side is a bug: on
 * this tender it turned 107 of 119 correctly-priced items into false FAILs, because a rate quoted
 * ~7% below the DSR book rate was being measured against a reference cut by 38%.
 */
class EscalationTest {

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "expected " + expected + " but was " + actual);
    }

    @Test
    void negativePercentageReducesTheScheduleTotal() {
        // Tender 232-25-26 Schedule A, the real figures from the document.
        assertMoney("1777943.90", RateMatchService.applyEscalation(
                new BigDecimal("2867651.45"), new BigDecimal("-38.00")));
    }

    @Test
    void positivePercentageRaisesTheScheduleTotal() {
        assertMoney("1085000.00", RateMatchService.applyEscalation(
                new BigDecimal("1000000.00"), new BigDecimal("8.50")));
    }

    @Test
    void atParLeavesTheTotalUnchanged() {
        // "AT Par" is parsed as 0%: exactly the schedule value, no loading either way.
        assertMoney("62490.80", RateMatchService.applyEscalation(
                new BigDecimal("62490.80"), BigDecimal.ZERO));
    }

    @Test
    void missingPercentageLeavesTheTotalUnchanged() {
        assertMoney("62490.80", RateMatchService.applyEscalation(
                new BigDecimal("62490.80"), null));
    }

    @Test
    void nullTotalStaysNull() {
        assertNull(RateMatchService.applyEscalation(null, new BigDecimal("-38.00")));
    }
}
