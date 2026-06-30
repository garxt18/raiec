package com.raiec.tools;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DEV utility (not a behavioural test): dumps the sample tender PDF's text to
 * sample-tenders/extracted-text.txt so we can design the parser around the real layout.
 * Run only this with:  mvnw -Dtest=PdfTextDumpTest test
 * Safe to delete once the parser exists.
 */
class PdfTextDumpTest {

    @Test
    void dumpSampleTenderText() throws Exception {
        Path dir = Paths.get("..", "sample-tenders");
        assumeTrue(Files.isDirectory(dir), "sample-tenders folder not found");

        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.pdf")) {
            for (Path pdf : stream) {
                try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    String text = stripper.getText(doc);

                    String base = pdf.getFileName().toString().replaceAll("\\.pdf$", "");
                    Path out = dir.resolve(base + ".txt");
                    Files.writeString(out, text);
                    System.out.println("WROTE " + out + " (" + text.length() + " chars, " + doc.getNumberOfPages() + " pages)");
                }
            }
        }
    }
}
