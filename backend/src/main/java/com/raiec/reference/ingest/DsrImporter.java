package com.raiec.reference.ingest;

import com.raiec.reference.entity.DsrItem;
import com.raiec.tender.ingest.PdfTextExtractor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the CPWD DSR rate-book volumes into DsrItem rows.
 *
 * Chapter work-items use dotted codes (4.1.4, 5.9.16.1, 14.75AS) and a description that wraps
 * across lines, ending with "{unit} {rate}". Basic-rate entries use 4-digit codes (no dots) and
 * are ignored automatically. Headings (4.0, 4.1) carry no rate and are skipped. Items are
 * de-duplicated by code so the English entry (which precedes its Hindi translation) is kept.
 */
@Component
public class DsrImporter {

    private static final Pattern CODE_LINE = Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)*[A-Z]{0,2})\\s+(.*)$");
    private static final Pattern NUMERIC = Pattern.compile("^[\\d,]+(\\.\\d+)?$");

    /**
     * Chapter marker, e.g. "SUB HEAD : 4.0". Work items only ever appear inside one of these,
     * and their code always starts with that chapter number. Everything before the first marker
     * is front matter and the Basic Rates tables, which are not work items.
     */
    private static final Pattern SUB_HEAD = Pattern.compile("^SUB\\s*HEAD\\s*:\\s*(\\d+)\\.0", Pattern.CASE_INSENSITIVE);

    /**
     * Units that actually occur in the rate books, with an optional leading count ("10 Nos",
     * "100 metre"). A row whose unit does not look like this is a mis-parsed table line —
     * typically a carriage/lead table where the last two columns are money, not unit+rate.
     */
    private static final Pattern UNIT_OK = Pattern.compile(
            "^(?:\\d{1,4}(?:\\.\\d+)?\\s+)?"
            + "(cum|sqm|sqcm|smt|metre|meter|rmt|rm|trm|km|cm|mm|m|100m"
            + "|each|no|nos|number|pair|dozen|set|bag|roll|sheet|point|job"
            + "|kg|quintal|qtl|tonne|ton|mt|litre|liter|ltr"
            + "|day|hour|shift|night|joint|sleeper|erc)$",
            Pattern.CASE_INSENSITIVE);

    private final PdfTextExtractor extractor;

    public DsrImporter(PdfTextExtractor extractor) {
        this.extractor = extractor;
    }

    public List<DsrItem> parse(File pdf, String edition) throws IOException {
        return parseText(extractor.extract(pdf), edition);
    }

    public List<DsrItem> parseText(String text, String edition) {
        List<DsrItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String code = null;
        StringBuilder acc = null;
        String chapter = null;

        for (String raw : text.split("\\r?\\n")) {
            String t = raw.replace('\u00A0', ' ').replace('\u2007', ' ').replace('\u202F', ' ').trim();

            Matcher sh = SUB_HEAD.matcher(t);
            if (sh.find()) {
                finalize(code, acc, items, seen, edition);
                code = null;
                acc = null;
                chapter = sh.group(1);
                continue;
            }
            if (isArtifact(t)) continue;

            // A code line only starts a new item inside a chapter, and only when its own chapter
            // matches. Otherwise it is wrapped description text that happens to begin with a
            // number (e.g. the continuation line "7.75 kg/sqm"), so it belongs to the current item.
            Matcher m = CODE_LINE.matcher(t);
            if (m.find() && chapter != null && chapter.equals(chapterOf(m.group(1)))) {
                finalize(code, acc, items, seen, edition);
                code = m.group(1);
                acc = new StringBuilder(m.group(2).trim());
            } else if (code != null && acc != null) {
                acc.append(' ').append(t);
            }
        }
        finalize(code, acc, items, seen, edition);
        return items;
    }

    private void finalize(String code, StringBuilder acc, List<DsrItem> items, Set<String> seen, String edition) {
        if (code == null || acc == null) return;
        Tail tail = parseTail(acc.toString());
        if (tail == null) return;          // heading row (no rate)
        if (!UNIT_OK.matcher(tail.unit()).matches()) return;  // mis-parsed table row, not an item
        if (!seen.add(code)) return;       // duplicate (e.g. Hindi translation) - keep first
        items.add(DsrItem.builder()
                .itemCode(code)
                .chapter(chapterOf(code))
                .description(tail.desc())
                .unit(tail.unit())
                .rate(money(tail.rate()))
                .edition(edition)
                .build());
    }

    private boolean isArtifact(String t) {
        if (t.isEmpty() || t.equals("`")) return true;
        String u = t.toUpperCase();
        if (u.startsWith("CODE NO DESCRIPTION") || u.startsWith("CODE DESCRIPTION")) return true;
        if (u.contains("SUB HEAD")) return true;
        if (u.contains("BASIC RATES")) return true;
        if (u.startsWith("DELHI SCHEDULE OF RATES") || u.startsWith("CPWD")) return true;
        if (u.contains("GOVERNMENT OF INDIA")) return true;
        if (t.matches("\\d{1,4}")) return true; // lone page number
        return false;
    }

    private String chapterOf(String code) {
        int dot = code.indexOf('.');
        return dot > 0 ? code.substring(0, dot) : code;
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
