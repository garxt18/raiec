package com.raiec.reference.ingest;

import com.raiec.reference.entity.DsrItem;
import com.raiec.tender.ingest.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DsrImporterTest {

    private static final Path VOL1 = Paths.get("..", "rate-books", "dsr-2021-vol1.pdf");

    @Test
    void parsesDsrChapterItems() throws Exception {
        assumeTrue(Files.exists(VOL1), "DSR vol1 not present locally");
        List<DsrItem> items = new DsrImporter(new PdfTextExtractor()).parse(VOL1.toFile(), "2023");

        assertTrue(items.size() > 500, "expected many chapter items, got " + items.size());

        DsrItem i414 = byCode(items, "4.1.4");
        assertNotNull(i414, "item 4.1.4 not found");
        assertEquals("cum", i414.getUnit());
        assertEquals(0, new BigDecimal("7780.30").compareTo(i414.getRate()));
        assertEquals("4", i414.getChapter());

        DsrItem i412 = byCode(items, "4.1.2");
        assertNotNull(i412, "item 4.1.2 not found");
        assertEquals(0, new BigDecimal("8340.85").compareTo(i412.getRate()));

        // basic-rate 4-digit codes must NOT be captured as chapter items
        assertTrue(items.stream().noneMatch(x -> x.getItemCode().equals("0982")));
    }

    private static DsrItem byCode(List<DsrItem> items, String code) {
        return items.stream().filter(x -> code.equals(x.getItemCode())).findFirst().orElse(null);
    }
}
