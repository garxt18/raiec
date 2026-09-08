package com.raiec.ratematch.service;

import com.raiec.ratematch.web.dto.ImpactSlice;
import com.raiec.ratematch.web.dto.RateMatchItem;
import com.raiec.ratematch.web.dto.ThresholdClustering;
import com.raiec.settings.entity.RateThresholds;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The analysis layer on top of the raw money: how few items carry the excess, where it
 * sits, and whether the variances look designed rather than derived.
 */
class FinancialAnalysisTest {

    private static RateMatchItem item(String schedule, String refSource,
                                      String quoted, String reference, String variancePct) {
        BigDecimal q = new BigDecimal(quoted);
        BigDecimal r = reference == null ? null : new BigDecimal(reference);
        return new RateMatchItem(schedule, "C-1", "Item", "DSR",
                BigDecimal.ONE, "cum", q, q,
                r, r, r == null ? null : q.subtract(r),
                refSource, BigDecimal.ZERO, true, false,
                variancePct == null ? null : new BigDecimal(variancePct),
                "OK");
    }

    private static RateThresholds limits() {
        return RateThresholds.builder()
                .warnPct(new BigDecimal("5.00"))
                .failPct(new BigDecimal("10.00"))
                .larValidityMonths(12)
                .fuzzyThreshold(new BigDecimal("0.45"))
                .build();
    }

    /* ------------------------------------------------------------------ concentration */

    @Test
    void concentrationNamesTheFewItemsCarryingMostOfTheExcess() {
        // 900 of the 1000 total excess sits in one line.
        List<RateMatchItem> items = List.of(
                item("A", "DSR", "1900", "1000", "90"),
                item("A", "DSR", "1050", "1000", "5"),
                item("A", "DSR", "1030", "1000", "3"),
                item("A", "DSR", "1020", "1000", "2"));
        assertEquals(1, RateMatchService.concentrationCount(items, new BigDecimal("1000")));
    }

    @Test
    void concentrationCountsEveryItemWhenExcessIsSpreadEvenly() {
        // Four equal shares: reaching 80% takes four items, not one.
        List<RateMatchItem> items = new ArrayList<>();
        for (int i = 0; i < 4; i++) items.add(item("A", "DSR", "1250", "1000", "25"));
        assertEquals(4, RateMatchService.concentrationCount(items, new BigDecimal("1000")));
    }

    @Test
    void concentrationIsZeroWhenNothingIsOverReference() {
        List<RateMatchItem> items = List.of(item("A", "DSR", "900", "1000", "-10"));
        assertEquals(0, RateMatchService.concentrationCount(items, BigDecimal.ZERO));
    }

    /* ------------------------------------------------------------------ source slices */

    @Test
    void unreferencedValueSurvivesAsItsOwnSliceRatherThanDisappearing() {
        // An unreferenced item has no excess, so a breakdown that only totals excess would
        // drop it -- losing precisely the money nobody has checked.
        List<RateMatchItem> items = List.of(
                item("A", "DSR 2023", "1200", "1000", "20"),
                item("A", null, "5000", null, null));

        List<ImpactSlice> slices = RateMatchService.sliceBySource(items, new BigDecimal("200"));
        ImpactSlice noRef = slices.stream().filter(s -> s.label().equals("No reference")).findFirst().orElseThrow();

        assertEquals(0, new BigDecimal("5000.00").compareTo(noRef.quotedValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(noRef.excessTotal()));
    }

    @Test
    void editionsCollapseToTheRateBookThatPricedTheWork() {
        List<RateMatchItem> items = List.of(
                item("A", "DSR 2023", "1200", "1000", "20"),
                item("A", "DSR 2021 (stale)", "1300", "1000", "30"));

        List<ImpactSlice> slices = RateMatchService.sliceBySource(items, new BigDecimal("500"));
        assertEquals(1, slices.size(), "two DSR editions are still one rate book");
        assertEquals("DSR", slices.get(0).label());
        assertEquals(2, slices.get(0).itemCount());
    }

    /* ------------------------------------------------------------------ clustering */

    @Test
    void clusteringIsFlaggedWhenRatesGatherJustUnderTheFlagLine() {
        // Twelve items, five of them parked between 7.5% and 10% -- the band a rate derived
        // from cost has no particular reason to land in.
        List<RateMatchItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) items.add(item("A", "DSR", "1098", "1000", "9.8"));
        for (int i = 0; i < 7; i++) items.add(item("A", "DSR", "1010", "1000", "1.0"));

        ThresholdClustering c = RateMatchService.detectClustering(items, limits());
        assertTrue(c.suspicious(), "5 of 12 in the band should be flagged, got " + c.sharePct() + "%");
        assertEquals(5, c.itemsInBand());
        assertEquals(12, c.comparedItems());
        assertEquals(0, new BigDecimal("7.50").compareTo(c.bandStartPct()));
    }

    @Test
    void clusteringStaysQuietOnASmallSample() {
        // Two of three items in the band is 67%, and means nothing at all. A detector that
        // fires here would be switched off before it ever caught anything real.
        List<RateMatchItem> items = List.of(
                item("A", "DSR", "1098", "1000", "9.8"),
                item("A", "DSR", "1097", "1000", "9.7"),
                item("A", "DSR", "1010", "1000", "1.0"));

        ThresholdClustering c = RateMatchService.detectClustering(items, limits());
        assertFalse(c.suspicious(), "three items cannot evidence a pattern");
        assertTrue(c.note().toLowerCase().contains("too few"), "the note should say why: " + c.note());
    }

    @Test
    void clusteringStaysQuietWhenVariancesAreSpread() {
        List<RateMatchItem> items = new ArrayList<>();
        String[] spread = {"1.0", "2.5", "-3.0", "0.5", "4.0", "-1.5", "3.0", "20.0", "6.0", "-8.0"};
        for (String v : spread) items.add(item("A", "DSR", "1000", "1000", v));

        ThresholdClustering c = RateMatchService.detectClustering(items, limits());
        assertFalse(c.suspicious());
        assertEquals(0, c.itemsInBand());
    }

    @Test
    void anItemExactlyOnTheFlagLineIsNotClustering() {
        // 10% is already a FAIL and gets flagged on its own. Counting it here as well would
        // let an openly over-quoted estimate masquerade as a concealment problem.
        List<RateMatchItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) items.add(item("A", "DSR", "1100", "1000", "10.0"));

        ThresholdClustering c = RateMatchService.detectClustering(items, limits());
        assertEquals(0, c.itemsInBand());
        assertFalse(c.suspicious());
    }

    @Test
    void clusteringReportsWhatTheBunchedItemsAreWorth() {
        List<RateMatchItem> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) items.add(item("A", "DSR", "1098", "1000", "9.8"));
        for (int i = 0; i < 7; i++) items.add(item("A", "DSR", "1010", "1000", "1.0"));

        ThresholdClustering c = RateMatchService.detectClustering(items, limits());
        // The share alone invites a shrug; the value is what makes it worth acting on.
        assertEquals(0, new BigDecimal("5490.00").compareTo(c.valueInBand()));
        assertEquals(0, new BigDecimal("490.00").compareTo(c.excessInBand()));
    }
}
