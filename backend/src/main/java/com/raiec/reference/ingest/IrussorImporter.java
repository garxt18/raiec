package com.raiec.reference.ingest;

import com.raiec.reference.entity.IrussorItem;
import com.raiec.tender.ingest.PdfTextExtractor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the IRUSSOR rate-book PDF into IrussorItem rows.
 * Format per priced item: "{6-digit code} {description...} {unit} {rate}".
 * Heading codes (e.g. 011000) have no unit/rate and are skipped.
 */
@Component
public class IrussorImporter {

    private static final Pattern CODE_LINE = Pattern.compile("^(\\d{6})\\s+(.*)$");
    private static final Pattern CHAPTER = Pattern.compile("^CHAPTER\\s*-?\\s*(\\d+)\\s*:?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC = Pattern.compile("^[\\d,]+(\\.\\d+)?$");

    private final PdfTextExtractor extractor;

    public IrussorImporter(PdfTextExtractor extractor) {
        this.extractor = extractor;
    }

    public List<IrussorItem> parse(File pdf, String edition) throws IOException {
        return parseText(extractor.extract(pdf), edition);
    }

    public List<IrussorItem> parseText(String text, String edition) {
        List<IrussorItem> items = new ArrayList<>();
        IrussorItem current = null;
        StringBuilder desc = null;
        String chapter = null;

        for (String raw : text.split("\\r?\\n")) {
            String t = raw.trim();
            if (t.isEmpty()) continue;

            Matcher ch = CHAPTER.matcher(t);
            if (ch.find()) {
                finalize(current, desc);
                current = null;
                desc = null;
                chapter = ch.group(1) + (ch.group(2).isBlank() ? "" : " - " + ch.group(2).trim());
                continue;
            }
            if (isArtifact(t)) continue;

            Matcher m = CODE_LINE.matcher(t);
            if (m.find()) {
                finalize(current, desc);
                String code = m.group(1);
                String rest = m.group(2).trim();
                Tail tail = parseTail(rest);
                if (tail != null) {
                    current = IrussorItem.builder()
                            .itemCode(code)
                            .chapter(chapter)
                            .description(tail.desc())
                            .unit(tail.unit())
                            .rate(money(tail.rate()))
                            .edition(edition)
                            .build();
                    items.add(current);
                    desc = new StringBuilder(tail.desc());
                } else {
                    current = null; // heading row (no rate)
                    desc = null;
                }
            } else if (current != null && desc != null) {
                desc.append(' ').append(t); // description continuation
            }
        }
        finalize(current, desc);
        return items;
    }

    private void finalize(IrussorItem item, StringBuilder desc) {
        if (item != null && desc != null) {
            item.setDescription(desc.toString().trim());
        }
    }

    private boolean isArtifact(String t) {
        return t.startsWith("IR Unified Standard Schedule")
                || t.startsWith("NR Unified Standard Schedule")
                || t.startsWith("Item No.")
                || t.startsWith("Page ")
                || t.startsWith("INDEX")
                || t.equals("`");
    }

    private Tail parseTail(String rest) {
        String[] tok = rest.split("\\s+");
        int n = tok.length;
        if (n < 2) return null;
        if (!NUMERIC.matcher(tok[n - 1]).matches()) return null;
        String rate = tok[n - 1];
        int unitIdx = n - 2;
        String unit = tok[unitIdx];
        int descEnd = unitIdx;
        if (unitIdx - 1 >= 0 && tok[unitIdx - 1].matches("\\d+")) {
            unit = tok[unitIdx - 1] + " " + unit;
            descEnd = unitIdx - 1;
        }
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < descEnd; i++) {
            if (d.length() > 0) d.append(' ');
            d.append(tok[i]);
        }
        return new Tail(d.toString(), unit, rate);
    }

    private static BigDecimal money(String s) {
        try {
            return new BigDecimal(s.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record Tail(String desc, String unit, String rate) {
    }
}
