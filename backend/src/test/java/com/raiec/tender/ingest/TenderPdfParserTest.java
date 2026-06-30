package com.raiec.tender.ingest;

import com.raiec.tender.entity.BreakupItem;
import com.raiec.tender.entity.RateSource;
import com.raiec.tender.entity.Schedule;
import com.raiec.tender.entity.ScheduleEntry;
import com.raiec.tender.entity.ScheduleEntryKind;
import com.raiec.tender.entity.Tender;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies Stage 1 parsing (NIT header + Section 2) against the real sample tender.
 * Skips automatically if the sample PDF is not present locally (it is git-ignored).
 */
class TenderPdfParserTest {

    private static final Path PDF = Paths.get("..", "sample-tenders", "viewNitPdf_5207353.pdf");

    private static void assertMoney(String expected, BigDecimal actual) {
        assertTrue(actual != null && new BigDecimal(expected).compareTo(actual) == 0,
                "expected " + expected + " but was " + actual);
    }

    @Test
    void parsesNitHeaderAndSchedules() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");

        TenderPdfParser parser = new TenderPdfParser(new PdfTextExtractor());
        Tender t = parser.parse(PDF.toFile());

        // ---- NIT header ----
        assertEquals("232-25-26", t.getTenderNo());
        assertEquals("Bikaner", t.getDivision());
        assertEquals("ADEN/HQ/Bikaner", t.getPost());
        assertTrue(t.getNameOfWork().contains("mechanized laundry"));
        assertMoney("9672884.53", t.getAdvertisedValue());
        assertMoney("193500.00", t.getEarnestMoney());
        assertEquals(60, t.getValidityOfOfferDays());
        assertEquals("Works - General", t.getContractType());
        assertEquals("SOUTH", t.getTenderingSection());
        assertEquals(LocalDateTime.of(2025, 12, 31, 15, 0), t.getClosingDateTime());

        // ---- schedules ----
        assertEquals(3, t.getSchedules().size());

        Schedule a = t.getSchedules().get(0);
        assertEquals("A", a.getCode());
        assertEquals(RateSource.DSR, a.getRateSource());
        assertEquals("2021", a.getEdition());
        assertMoney("9301942.03", a.getTotalAmount());
        assertEquals(6, a.getEntries().size());
        ScheduleEntry g1 = a.getEntries().get(0);
        assertEquals(ScheduleEntryKind.GROUP, g1.getKind());
        assertMoney("2867651.45", g1.getBasicValue());
        assertMoney("-38.00", g1.getEscalationPct());
        assertFalse(g1.isAtPar());
        assertTrue(g1.getDescription().contains("Concrete Work"));

        Schedule b = t.getSchedules().get(1);
        assertEquals("B", b.getCode());
        assertEquals(RateSource.IRUSSOR, b.getRateSource());
        assertMoney("62490.80", b.getTotalAmount());
        assertEquals(1, b.getEntries().size());
        assertTrue(b.getEntries().get(0).isAtPar());

        Schedule c = t.getSchedules().get(2);
        assertEquals("C", c.getCode());
        assertEquals(RateSource.NS, c.getRateSource());
        assertMoney("308451.70", c.getTotalAmount());
        assertEquals(6, c.getEntries().size());

        ScheduleEntry ns1 = c.getEntries().get(0);
        assertEquals(ScheduleEntryKind.NS_ITEM, ns1.getKind());
        assertEquals("NS 1", ns1.getItemCode());
        assertMoney("20", ns1.getQuantity());
        assertEquals("Each", ns1.getQtyUnit());
        assertMoney("5066.00", ns1.getUnitRate());
        assertTrue(ns1.isAtPar());
        assertTrue(ns1.getDescription().contains("Maxxair"));

        ScheduleEntry ns3 = c.getEntries().get(2);
        assertEquals("NS 3", ns3.getItemCode());
        assertEquals("Running Metre", ns3.getQtyUnit());
        assertMoney("200.85", ns3.getUnitRate());

        // ---- Section 3 breakup (Stage 2) ----
        ScheduleEntry concrete = a.getEntries().get(0); // Item-1: Concrete (ch 04 & 05)
        assertFalse(concrete.getBreakupItems().isEmpty());

        BreakupItem i414 = findItem(concrete, "4.1.4");
        assertNotNull(i414, "breakup item 4.1.4 not found");
        assertFalse(i414.isHeading());
        assertEquals("cum", i414.getUnit());
        assertMoney("20", i414.getQuantity());
        assertMoney("7226.95", i414.getRate());
        assertMoney("144539", i414.getAmount());

        BreakupItem i418 = findItem(concrete, "4.1.8");
        assertNotNull(i418, "breakup item 4.1.8 not found");
        assertMoney("6326.05", i418.getRate());

        BreakupItem h40 = findItem(concrete, "4.0");
        assertNotNull(h40, "heading 4.0 not found");
        assertTrue(h40.isHeading());
        assertTrue(h40.getDescription().contains("CONCRETE WORK"));

        // IRUSSOR breakup
        ScheduleEntry irussorGroup = b.getEntries().get(0);
        BreakupItem jcb = findItem(irussorGroup, "211201");
        assertNotNull(jcb, "IRUSSOR item 211201 not found");
        assertEquals("Hour", jcb.getUnit());
        assertMoney("50", jcb.getQuantity());
        assertMoney("864.02", jcb.getRate());
        BreakupItem hydra = findItem(irussorGroup, "211202");
        assertNotNull(hydra, "IRUSSOR item 211202 not found");
        assertMoney("964.49", hydra.getRate());
    }

    @Test
    void parsesMultiScheduleTender236() throws Exception {
        Path pdf = Paths.get("..", "sample-tenders", "viewNitPdf_5209279.pdf");
        assumeTrue(Files.exists(pdf), "236 sample PDF not present locally");
        Tender t = new TenderPdfParser(new PdfTextExtractor()).parse(pdf.toFile());

        assertEquals("236-25-26", t.getTenderNo());
        assertTrue(t.getSchedules().size() >= 4, "expected at least 4 schedules");

        Schedule a = t.getSchedules().get(0);
        assertEquals("A", a.getCode());
        assertEquals(RateSource.DSR, a.getRateSource());
        assertEquals("2021", a.getEdition());            // from "DSR-21"
        assertMoney("39064857.96", a.getTotalAmount());
        assertEquals(9, a.getEntries().size());
        assertMoney("-38.00", a.getEntries().get(0).getEscalationPct());
        assertFalse(a.getEntries().get(0).getBreakupItems().isEmpty(), "schedule A breakup should be populated");

        Schedule b = t.getSchedules().get(1);
        assertEquals("2020", b.getEdition());            // from "DSR-20", 2-digit normalised

        Schedule c = t.getSchedules().get(2);
        assertEquals(RateSource.IRUSSOR, c.getRateSource());
        ScheduleEntry cFirst = c.getEntries().get(0);
        assertFalse(cFirst.isAtPar());
        assertMoney("15.50", cFirst.getEscalationPct());  // bare positive escalation
    }

    @Test
    void parsesSingleScheduleTender153() throws Exception {
        Path pdf = Paths.get("..", "sample-tenders", "viewNitPdf_5177357.pdf");
        assumeTrue(Files.exists(pdf), "153 sample PDF not present locally");
        Tender t = new TenderPdfParser(new PdfTextExtractor()).parse(pdf.toFile());

        assertEquals("153-2025", t.getTenderNo());
        assertEquals(1, t.getSchedules().size());
        Schedule a = t.getSchedules().get(0);
        assertEquals(RateSource.IRUSSOR, a.getRateSource());
        assertEquals("2021", a.getEdition());
        assertMoney("7865367.89", a.getTotalAmount());
        assertTrue(a.getEntries().get(0).isAtPar());
    }

    @Test
    void parsesMixedNsTender160() throws Exception {
        Path pdf = Paths.get("..", "sample-tenders", "viewNitPdf_5204559.pdf");
        assumeTrue(Files.exists(pdf), "160 sample PDF not present locally");
        Tender t = new TenderPdfParser(new PdfTextExtractor()).parse(pdf.toFile());

        assertEquals("160-2025", t.getTenderNo());
        assertEquals(3, t.getSchedules().size());

        // Schedule B "N.S. ITEMS FOR CIVIL WORK": recognised as NS, 3 NS items (inline + group + inline).
        Schedule b = t.getSchedules().get(1);
        assertEquals(RateSource.NS, b.getRateSource());
        assertEquals(3, b.getEntries().size());
        assertMoney("43567.82", b.getEntries().get(0).getUnitRate()); // inline NS01

        // Schedule C "N.S. ITEMS (FOR ELECTRICAL WORK)": NS, 5 group NS items.
        Schedule c = t.getSchedules().get(2);
        assertEquals(RateSource.NS, c.getRateSource());
        assertEquals(5, c.getEntries().size());
        // NS/2 group in B got its build-up breakup attached (mapped by serial).
        assertFalse(b.getEntries().get(1).getBreakupItems().isEmpty());
    }

    @Test
    void parsesContinuousNumberingTender154() throws Exception {
        Path pdf = Paths.get("..", "sample-tenders", "viewNitPdf_5179896.pdf");
        assumeTrue(Files.exists(pdf), "154 sample PDF not present locally");
        Tender t = new TenderPdfParser(new PdfTextExtractor()).parse(pdf.toFile());

        assertEquals("154-2025", t.getTenderNo());
        Schedule b = t.getSchedules().get(1);
        assertEquals(RateSource.NS, b.getRateSource());

        // The single "NS ITEMS" group must get its breakup attached via description match
        // even though item numbering is continuous across schedules (Item- 2).
        ScheduleEntry grp = b.getEntries().get(0);
        BreakupItem fab = grp.getBreakupItems().stream()
                .filter(x -> !x.isHeading() && x.getRate() != null).findFirst().orElse(null);
        assertNotNull(fab, "NS ITEMS breakup should be attached");
        assertTrue(fab.getDescription().contains("Glued insulated joints"));
        assertMoney("19651.18", fab.getRate());
    }

    private static BreakupItem findItem(ScheduleEntry group, String code) {
        return group.getBreakupItems().stream()
                .filter(bi -> code.equals(bi.getItemCode()))
                .findFirst()
                .orElse(null);
    }
}
