package com.raiec.settings;

import com.raiec.settings.entity.RateThresholds;
import com.raiec.settings.service.ThresholdService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The tolerance bands are department settings, so a bad combination must be rejected at the
 * service rather than quietly producing verdicts nobody can explain.
 */
@SpringBootTest
@Transactional
class ThresholdServiceTest {

    @Autowired
    private ThresholdService service;

    @Test
    void seedsTheShippedDefaultsOnFirstRead() {
        RateThresholds t = service.current();
        assertEquals(0, new BigDecimal("5.00").compareTo(t.getWarnPct()));
        assertEquals(0, new BigDecimal("10.00").compareTo(t.getFailPct()));
        assertEquals(12, t.getLarValidityMonths());
    }

    @Test
    void updatesOnlyTheFieldsSupplied() {
        service.update(new BigDecimal("6"), null, null, null, "admin");
        RateThresholds t = service.current();
        assertEquals(0, new BigDecimal("6").compareTo(t.getWarnPct()));
        // untouched
        assertEquals(0, new BigDecimal("10.00").compareTo(t.getFailPct()));
        assertEquals(12, t.getLarValidityMonths());
    }

    @Test
    void recordsWhoChangedIt() {
        service.update(null, new BigDecimal("12"), null, null, "officer1");
        assertEquals("officer1", service.current().getUpdatedBy());
    }

    @Test
    void warnAboveFailIsRejected() {
        // If WARN were above FAIL, the WARN band could never be reached: anything past WARN
        // would already have failed.
        var e = assertThrows(IllegalArgumentException.class,
                () -> service.update(new BigDecimal("20"), new BigDecimal("10"), null, null, "admin"));
        assertEquals(true, e.getMessage().contains("below the FAIL threshold"));
    }

    @Test
    void negativeToleranceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.update(new BigDecimal("-1"), null, null, null, "admin"));
    }

    @Test
    void larValidityOutsideOneToOneTwentyMonthsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service.update(null, null, 0, null, "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> service.update(null, null, 200, null, "admin"));
    }

    @Test
    void fuzzyThresholdMustBeAProperFraction() {
        assertThrows(IllegalArgumentException.class,
                () -> service.update(null, null, null, BigDecimal.ZERO, "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> service.update(null, null, null, BigDecimal.ONE, "admin"));
    }
}
