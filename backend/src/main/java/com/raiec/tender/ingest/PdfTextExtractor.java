package com.raiec.tender.ingest;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * Extracts plain text from a tender PDF using Apache PDFBox.
 * Position-sorted so columns come out in a left-to-right, top-to-bottom reading order.
 */
@Component
public class PdfTextExtractor {

    public String extract(File pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return strip(doc);
        }
    }

    public String extract(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return strip(doc);
        }
    }

    private String strip(PDDocument doc) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(doc);
    }
}
