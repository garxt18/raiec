package com.raiec.tools;

import com.raiec.reference.entity.DsrItem;
import com.raiec.reference.entity.IrussorItem;
import com.raiec.reference.ingest.DsrImporter;
import com.raiec.reference.ingest.IrussorImporter;
import com.raiec.tender.ingest.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DEV diagnostic: measures how much of each imported rate book is actually usable.
 * Counts rows whose "unit" is numeric (a mis-parse of a wrapped table row) or whose
 * rate is implausible, so we can see the real signal-to-noise ratio of the importers.
 */
class RateBookQualityProbeTest {

    private static final Path DSR1 = Paths.get("..", "rate-books", "dsr-2021-vol1.pdf");
    private static final Path DSR2 = Paths.get("..", "rate-books", "dsr-2021-vol2.pdf");
    private static final Path IRUSSOR = Paths.get("..", "rate-books", "irussor-2021.pdf");

    @Test
    void reportDsrQuality() throws Exception {
        assumeTrue(Files.exists(DSR1), "dsr vol1 not present");
        DsrImporter imp = new DsrImporter(new PdfTextExtractor());
        List<DsrItem> v1 = imp.parse(DSR1.toFile(), "2021");
        report("DSR vol1", v1, DsrItem::getUnit, DsrItem::getRate, DsrItem::getItemCode, DsrItem::getDescription);
        if (Files.exists(DSR2)) {
            List<DsrItem> v2 = imp.parse(DSR2.toFile(), "2021");
            report("DSR vol2", v2, DsrItem::getUnit, DsrItem::getRate, DsrItem::getItemCode, DsrItem::getDescription);
        }
    }

    @Test
    void reportIrussorQuality() throws Exception {
        assumeTrue(Files.exists(IRUSSOR), "irussor not present");
        List<IrussorItem> items = new IrussorImporter(new PdfTextExtractor()).parse(IRUSSOR.toFile(), "2021");
        report("IRUSSOR", items, IrussorItem::getUnit, IrussorItem::getRate,
                IrussorItem::getItemCode, IrussorItem::getDescription);
    }

    private <T> void report(String label, List<T> items,
                            Function<T, String> unitFn, Function<T, BigDecimal> rateFn,
                            Function<T, String> codeFn, Function<T, String> descFn) {
        int total = items.size();
        int numericUnit = 0, nullRate = 0, zeroRate = 0, shortDesc = 0, good = 0;
        Map<String, Integer> unitHistogram = new TreeMap<>();

        for (T it : items) {
            String unit = unitFn.apply(it);
            BigDecimal rate = rateFn.apply(it);
            String desc = descFn.apply(it);
            boolean bad = false;

            if (unit == null || unit.matches("[\\d.,]+")) { numericUnit++; bad = true; }
            if (rate == null) { nullRate++; bad = true; }
            else if (rate.signum() == 0) { zeroRate++; bad = true; }
            if (desc == null || desc.trim().length() < 15) { shortDesc++; bad = true; }
            if (!bad) good++;

            String u = unit == null ? "(null)" : unit.toLowerCase();
            unitHistogram.merge(u, 1, Integer::sum);
        }

        System.out.println("=====================================================");
        System.out.println("  " + label);
        System.out.println("=====================================================");
        System.out.println("  total parsed rows : " + total);
        System.out.println("  numeric 'unit'    : " + numericUnit + "   <-- mis-parsed table rows");
        System.out.println("  null rate         : " + nullRate);
        System.out.println("  zero rate         : " + zeroRate);
        System.out.println("  desc < 15 chars   : " + shortDesc);
        System.out.println("  CLEAN rows        : " + good
                + "  (" + (total == 0 ? 0 : good * 100 / total) + "%)");

        System.out.println("  -- top 25 'units' by frequency --");
        unitHistogram.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(25)
                .forEach(e -> System.out.println("     " + String.format("%6d", e.getValue()) + "  " + e.getKey()));

        System.out.println("  -- 10 sample BAD rows --");
        items.stream()
                .filter(it -> {
                    String u = unitFn.apply(it);
                    return u == null || u.matches("[\\d.,]+");
                })
                .limit(10)
                .forEach(it -> System.out.println("     [" + codeFn.apply(it) + "] unit='" + unitFn.apply(it)
                        + "' rate=" + rateFn.apply(it) + " desc='" + trim(descFn.apply(it)) + "'"));

        System.out.println("  -- 10 sample GOOD rows --");
        items.stream()
                .filter(it -> {
                    String u = unitFn.apply(it);
                    BigDecimal r = rateFn.apply(it);
                    String d = descFn.apply(it);
                    return u != null && !u.matches("[\\d.,]+") && r != null && r.signum() > 0
                            && d != null && d.trim().length() >= 15;
                })
                .limit(10)
                .forEach(it -> System.out.println("     [" + codeFn.apply(it) + "] unit='" + unitFn.apply(it)
                        + "' rate=" + rateFn.apply(it) + " desc='" + trim(descFn.apply(it)) + "'"));
        System.out.println();
    }

    private static String trim(String s) {
        if (s == null) return "(null)";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 70 ? t.substring(0, 70) + "..." : t;
    }
}
