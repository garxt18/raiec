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
 * DEV utility: probes the first pages of each rate-book PDF to detect whether it is text-based
 * (PDFBox extracts text) or scanned (little/no text -> OCR needed), and dumps a sample.
 */
class RateBookProbeTest {

    private static final String[] FILES = {
            "irussor-2021.pdf", "dsr-2021-vol1.pdf", "dsr-2021-vol2.pdf"
    };

    @Test
    void dumpDsrVol1Full() throws Exception {
        Path pdf = Paths.get("..", "rate-books", "dsr-2021-vol1.pdf");
        assumeTrue(Files.exists(pdf), "dsr vol1 not present");
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            String text = s.getText(doc);
            Files.writeString(Paths.get("..", "rate-books", "dsr-2021-vol1-full.txt"), text);
            System.out.println("FULL vol1 chars=" + text.length() + " pages=" + doc.getNumberOfPages());
        }
    }

    @Test
    void probeRateBooks() throws Exception {
        Path dir = Paths.get("..", "rate-books");
        assumeTrue(Files.isDirectory(dir), "rate-books folder not found");

        for (String name : FILES) {
            Path pdf = dir.resolve(name);
            if (!Files.exists(pdf)) {
                System.out.println("MISSING " + name);
                continue;
            }
            try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
                int pages = doc.getNumberOfPages();
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(8, pages));
                String text = stripper.getText(doc);
                Path out = dir.resolve(name.replaceAll("\\.pdf$", "") + "-head.txt");
                Files.writeString(out, text);
                System.out.println("PROBE " + name + " pages=" + pages
                        + " headChars=" + text.length()
                        + " -> " + (text.trim().length() < 50 ? "SCANNED?(no text)" : "TEXT"));

                if (name.contains("dsr") && pages > 60) {
                    PDFTextStripper mid = new PDFTextStripper();
                    mid.setSortByPosition(true);
                    mid.setStartPage(50);
                    mid.setEndPage(54);
                    String midText = mid.getText(doc);
                    Files.writeString(dir.resolve(name.replaceAll("\\.pdf$", "") + "-mid.txt"), midText);
                    System.out.println("  MID " + name + " pages50-54 chars=" + midText.length());
                }
            }
        }
    }
}
