package com.raiec.reference.ingest;

import com.raiec.reference.entity.IrussorItem;
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

class IrussorImporterTest {

    private static final Path PDF = Paths.get("..", "rate-books", "irussor-2021.pdf");

    @Test
    void parsesIrussorRateBook() throws Exception {
        assumeTrue(Files.exists(PDF), "IRUSSOR rate book not present locally");
        List<IrussorItem> items = new IrussorImporter(new PdfTextExtractor()).parse(PDF.toFile(), "2021");

        assertTrue(items.size() > 200, "expected many items, got " + items.size());

        IrussorItem i = byCode(items, "011031");
        assertNotNull(i, "item 011031 not found");
        assertEquals(0, new BigDecimal("319.97").compareTo(i.getRate()));
        assertEquals("Cum", i.getUnit());

        IrussorItem j = byCode(items, "012011");
        assertNotNull(j, "item 012011 not found");
        assertEquals(0, new BigDecimal("107.35").compareTo(j.getRate()));
    }

    private static IrussorItem byCode(List<IrussorItem> items, String code) {
        return items.stream().filter(x -> code.equals(x.getItemCode())).findFirst().orElse(null);
    }
}
