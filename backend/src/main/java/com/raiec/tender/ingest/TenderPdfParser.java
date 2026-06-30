package com.raiec.tender.ingest;

import com.raiec.tender.entity.RateSource;
import com.raiec.tender.entity.Schedule;
import com.raiec.tender.entity.ScheduleEntry;
import com.raiec.tender.entity.ScheduleEntryKind;
import com.raiec.tender.entity.BreakupItem;
import com.raiec.tender.entity.Tender;
import com.raiec.tender.entity.TenderStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an IREPS tender PDF into the Tender -> Schedule -> ScheduleEntry tree.
 *
 * Stage 1 (this class): NIT header (Section 1) + Schedule (Section 2), i.e. the tender
 * metadata, its schedules, the DSR/IRUSSOR chapter groups (with escalation) and the
 * Non-Scheduled items. Section 3 (item breakup) is handled in a later stage.
 */
@Component
public class TenderPdfParser {

    private static final DateTimeFormatter D_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter D_DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** NIT header labels in the exact order they appear in the flattened text. */
    private static final List<String> NIT_LABELS = List.of(
            "Name of Work", "Bidding type", "Tender Type", "Bidding System",
            "Tender Closing Date Time", "Date Time Of Uploading Tender",
            "Pre-Bid Conference Required", "Pre-Bid Conference Date Time",
            "Advertised Value", "Tendering Section", "Bidding Style", "Bidding Unit",
            "Earnest Money (Rs.)", "Validity of Offer ( Days)", "Tender Doc. Cost (Rs.)", "Period of Completion",
            "Contract Type", "Contract Category", "Bidding Start Date",
            "Are JV allowed to bid", "Number of JV Member Allowed",
            "Are Consortium allowed to bid", "Number of Consortium Member Allowed",
            "Ranking Order For Bids", "Expenditure Type"
    );

    private static final Pattern P_DIVISION = Pattern.compile("^(.*?) DIVISION-ENGINEERING/NORTH WESTERN RLY");
    private static final Pattern P_TENDER_NO = Pattern.compile("^Tender No:\\s*(\\S+)\\s+Closing Date/Time:");
    private static final Pattern P_FOOTER = Pattern.compile("^Page \\d+ of \\d+ Run Date/Time:");

    private static final String ESCL = "AT Par|\\([+-]\\)\\s*[\\d.]+|[-+]?\\d+\\.\\d+";
    private static final Pattern P_SCHEDULE = Pattern.compile("^Schedule \\(\\)\\s*([A-Za-z])-(.+)$");
    private static final Pattern P_SCHEDULE_AMOUNT = Pattern.compile("(\\d[\\d,]*\\.\\d{2})");
    private static final Pattern P_GROUP = Pattern.compile(
            "^(?:\\d+\\s+)?Please see Item Breakup for details\\.\\s+([-\\d.,]+)\\s+(" + ESCL + ")\\s+([-\\d.,]+)$");
    private static final Pattern P_NS_START = Pattern.compile("^NS\\s*\\d+\\b");
    private static final Pattern P_NS_ROW = Pattern.compile(
            "^NS\\s*(\\d+)\\s+([\\d.,]+)\\s+(.+?)\\s+([\\d.,]+)\\s+([\\d.,]+)\\s+(" + ESCL + ")\\s+([-\\d.,]+)$");
    private static final Pattern P_DESCRIPTION = Pattern.compile("^(?:\\d+\\s+)?Description:-\\s*(.*)$");
    private static final Pattern P_EDITION = Pattern.compile("(?:DSR|IRUSSOR)\\s*-?\\s*(\\d{2,4})");
    private static final Pattern P_POST = Pattern.compile("under\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    // Section 3 (item breakup)
    private static final String CODE = "(?:\\d+(?:\\.\\d+)+[A-Z]*|\\d{4,}[A-Z]*)";
    private static final Pattern P_BREAKUP_SCHEDULE = Pattern.compile("^Schedule Schedule ([A-Za-z])-");
    private static final Pattern P_ITEM_GROUP = Pattern.compile("^Item-\\s*(\\d+)\\s*(.*)$");
    private static final Pattern P_TOTAL = Pattern.compile("^Total\\s+([-\\d.,]+)$");
    private static final Pattern P_CODE_LINE = Pattern.compile("^(?:(\\d{1,3})\\s+)?(" + CODE + ")\\s+(.*)$");
    private static final Pattern P_NUMERIC = Pattern.compile("^-?[\\d,]*\\.?\\d+$");

    private final PdfTextExtractor extractor;

    public TenderPdfParser(PdfTextExtractor extractor) {
        this.extractor = extractor;
    }

    public Tender parse(File pdf) throws IOException {
        return parseText(extractor.extract(pdf));
    }

    public Tender parse(byte[] pdfBytes) throws IOException {
        return parseText(extractor.extract(pdfBytes));
    }

    /** Parses already-extracted PDF text (kept public for easy unit testing). */
    public Tender parseText(String rawText) {
        String[] rawLines = rawText.split("\\r?\\n");

        String tenderNo = null;
        String division = null;
        for (String raw : rawLines) {
            String line = raw.trim();
            if (division == null) {
                Matcher m = P_DIVISION.matcher(line);
                if (m.find()) {
                    division = capitalizeWords(m.group(1).trim());
                }
            }
            if (tenderNo == null) {
                Matcher m = P_TENDER_NO.matcher(line);
                if (m.find()) {
                    tenderNo = m.group(1);
                }
            }
            if (division != null && tenderNo != null) {
                break;
            }
        }

        List<String> lines = stripArtifacts(rawLines);

        int idxNit = indexOfLine(lines, "1. NIT HEADER");
        int idxSch = indexOfLine(lines, "2. SCHEDULE");
        int idxBreak = indexOfLine(lines, "3. ITEM BREAKUP");
        int idxElig = indexOfLine(lines, "4. ELIGIBILITY CONDITIONS");

        List<String> nitLines = safeSub(lines, idxNit + 1, idxSch);
        List<String> schLines = safeSub(lines, idxSch + 1, idxBreak >= 0 ? idxBreak : lines.size());
        List<String> breakupLines = safeSub(lines, idxBreak + 1, idxElig >= 0 ? idxElig : lines.size());

        Tender tender = parseNitHeader(nitLines);
        tender.setTenderNo(tenderNo);
        tender.setDivision(division);
        tender.setStatus(TenderStatus.OCR_EXTRACTED);

        parseSchedules(schLines, tender);
        parseBreakup(breakupLines, tender);
        return tender;
    }

    // ---------------- NIT header ----------------

    private Tender parseNitHeader(List<String> nitLines) {
        String flat = String.join(" ", nitLines).replaceAll("\\s+", " ").trim();
        Map<String, String> v = extractLabeledValues(flat);

        String nameOfWork = blankToNull(v.get("Name of Work"));
        return Tender.builder()
                .nameOfWork(nameOfWork)
                .post(extractPost(nameOfWork))
                .biddingType(blankToNull(v.get("Bidding type")))
                .tenderType(blankToNull(v.get("Tender Type")))
                .biddingSystem(blankToNull(v.get("Bidding System")))
                .closingDateTime(parseDateTime(v.get("Tender Closing Date Time")))
                .uploadingDateTime(parseDateTime(v.get("Date Time Of Uploading Tender")))
                .advertisedValue(parseMoney(v.get("Advertised Value")))
                .tenderingSection(blankToNull(v.get("Tendering Section")))
                .biddingStyle(blankToNull(v.get("Bidding Style")))
                .earnestMoney(parseMoney(v.get("Earnest Money (Rs.)")))
                .validityOfOfferDays(parseIntSafe(v.get("Validity of Offer ( Days)")))
                .tenderDocCost(parseMoney(v.get("Tender Doc. Cost (Rs.)")))
                .periodOfCompletion(blankToNull(v.get("Period of Completion")))
                .contractType(blankToNull(v.get("Contract Type")))
                .contractCategory(blankToNull(v.get("Contract Category")))
                .biddingStartDate(parseDate(v.get("Bidding Start Date")))
                .rankingOrder(blankToNull(v.get("Ranking Order For Bids")))
                .expenditureType(blankToNull(v.get("Expenditure Type")))
                .build();
    }

    /** Splits the flattened header by walking the known labels in order. */
    private Map<String, String> extractLabeledValues(String flat) {
        Map<String, String> out = new LinkedHashMap<>();
        int pos = 0;
        for (int i = 0; i < NIT_LABELS.size(); i++) {
            String label = NIT_LABELS.get(i);
            int start = flat.indexOf(label, pos);
            if (start < 0) {
                out.put(label, "");
                continue;
            }
            int valStart = start + label.length();
            int valEnd = flat.length();
            for (int j = i + 1; j < NIT_LABELS.size(); j++) {
                int n = flat.indexOf(NIT_LABELS.get(j), valStart);
                if (n >= 0) {
                    valEnd = n;
                    break;
                }
            }
            out.put(label, flat.substring(valStart, valEnd).trim());
            pos = valEnd;
        }
        return out;
    }

    // ---------------- Section 2: schedules ----------------

    private void parseSchedules(List<String> lines, Tender tender) {
        Schedule current = null;
        int serial = 0;
        int scheduleOrder = 0;
        ScheduleEntry pending = null;
        StringBuilder desc = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            Matcher sh = P_SCHEDULE.matcher(line);
            if (sh.find()) {
                flush(pending, desc);
                pending = null;
                desc = null;
                String rest = sh.group(2).trim();
                BigDecimal total = null;
                String name = rest;
                Matcher am = P_SCHEDULE_AMOUNT.matcher(rest);
                if (am.find()) {
                    total = parseMoney(am.group(1));
                    name = rest.substring(0, am.start()).trim();
                }
                current = Schedule.builder()
                        .code(sh.group(1))
                        .name(name)
                        .rateSource(rateSourceFromName(name))
                        .edition(extractEdition(name))
                        .totalAmount(total)
                        .biddingUnit("Above/Below/Par")
                        .displayOrder(++scheduleOrder)
                        .build();
                tender.addSchedule(current);
                serial = 0;
                continue;
            }

            if (current == null) {
                continue;
            }

            Matcher gr = P_GROUP.matcher(line);
            if (gr.find()) {
                flush(pending, desc);
                Escalation e = parseEscalation(gr.group(2));
                ScheduleEntry entry = ScheduleEntry.builder()
                        .serialNo(++serial)
                        .kind(ScheduleEntryKind.GROUP)
                        .basicValue(parseMoney(gr.group(1)))
                        .escalationPct(e.pct())
                        .atPar(e.atPar())
                        .amount(parseMoney(gr.group(3)))
                        .build();
                current.addEntry(entry);
                pending = entry;
                desc = null;
                continue;
            }

            if (P_NS_START.matcher(line).find()) {
                String joined = line;
                int j = i;
                Matcher nr = P_NS_ROW.matcher(joined);
                while (!nr.matches() && j + 1 < lines.size() && (j - i) < 3) {
                    j++;
                    joined = joined + " " + lines.get(j);
                    nr = P_NS_ROW.matcher(joined);
                }
                if (nr.matches()) {
                    flush(pending, desc);
                    Escalation e = parseEscalation(nr.group(6));
                    ScheduleEntry entry = ScheduleEntry.builder()
                            .serialNo(++serial)
                            .kind(ScheduleEntryKind.NS_ITEM)
                            .itemCode("NS " + nr.group(1))
                            .quantity(parseMoney(nr.group(2)))
                            .qtyUnit(nr.group(3).trim())
                            .unitRate(parseMoney(nr.group(4)))
                            .basicValue(parseMoney(nr.group(5)))
                            .escalationPct(e.pct())
                            .atPar(e.atPar())
                            .amount(parseMoney(nr.group(7)))
                            .build();
                    current.addEntry(entry);
                    pending = entry;
                    desc = null;
                    i = j;
                    continue;
                }
            }

            Matcher dm = P_DESCRIPTION.matcher(line);
            if (dm.find()) {
                if (pending != null) {
                    desc = new StringBuilder(dm.group(1).trim());
                }
                continue;
            }

            if (isJunk(line)) {
                continue;
            }

            // continuation of the current description
            if (pending != null && desc != null) {
                if (desc.length() > 0) {
                    desc.append(' ');
                }
                desc.append(line);
            }
        }
        flush(pending, desc);
    }

    private void flush(ScheduleEntry entry, StringBuilder desc) {
        if (entry != null && desc != null) {
            entry.setDescription(desc.toString().trim());
        }
    }

    // ---------------- Section 3: item breakup ----------------

    private void parseBreakup(List<String> lines, Tender tender) {
        Schedule curSchedule = null;
        ScheduleEntry curGroup = null;
        BreakupItem curItem = null;
        StringBuilder itemDesc = null;
        int order = 0;
        boolean awaitingHeader = false;

        for (String line : lines) {
            Matcher sm = P_BREAKUP_SCHEDULE.matcher(line);
            if (sm.find()) {
                flushBreakup(curItem, itemDesc);
                curSchedule = findScheduleByCode(tender, sm.group(1));
                curGroup = null;
                curItem = null;
                itemDesc = null;
                awaitingHeader = false;
                continue;
            }

            Matcher im = P_ITEM_GROUP.matcher(line);
            if (im.find()) {
                flushBreakup(curItem, itemDesc);
                int n = Integer.parseInt(im.group(1));
                String gdesc = im.group(2) != null ? im.group(2).trim() : "";
                ScheduleEntry g = matchEntryByDescription(curSchedule, gdesc);
                if (g == null) {
                    g = findEntryBySerial(curSchedule, n);
                }
                curGroup = g;
                curItem = null;
                itemDesc = null;
                order = 0;
                awaitingHeader = true;
                continue;
            }

            if (line.startsWith("S No.")) {
                awaitingHeader = false;
                continue;
            }
            if (awaitingHeader) {
                continue;
            }

            Matcher tm = P_TOTAL.matcher(line);
            if (tm.find()) {
                flushBreakup(curItem, itemDesc);
                curItem = null;
                itemDesc = null;
                continue;
            }

            if (curGroup == null) {
                continue;
            }

            BreakupRow row = detectBreakupRow(line);
            if (row != null) {
                flushBreakup(curItem, itemDesc);
                BreakupItem.BreakupItemBuilder b = BreakupItem.builder()
                        .serialNo(row.serial())
                        .itemCode(row.code())
                        .displayOrder(++order);
                String desc;
                if (row.tail() != null) {
                    b.heading(false)
                            .unit(row.tail().unit())
                            .quantity(parseMoney(row.tail().qty()))
                            .rate(parseMoney(row.tail().rate()))
                            .amount(parseMoney(row.tail().amount()));
                    desc = row.tail().desc();
                } else {
                    b.heading(true);
                    desc = row.rest();
                }
                b.description(desc);
                BreakupItem item = b.build();
                curGroup.addBreakupItem(item);
                curItem = item;
                itemDesc = new StringBuilder(desc);
                continue;
            }

            if (isJunk(line)) {
                continue;
            }

            // code-less leading heading (e.g. IRUSSOR parent paragraph), or description continuation
            if (curItem == null) {
                BreakupItem item = BreakupItem.builder()
                        .heading(true)
                        .displayOrder(++order)
                        .description(line)
                        .build();
                curGroup.addBreakupItem(item);
                curItem = item;
                itemDesc = new StringBuilder(line);
            } else {
                if (itemDesc.length() > 0) {
                    itemDesc.append(' ');
                }
                itemDesc.append(line);
            }
        }
        flushBreakup(curItem, itemDesc);
    }

    private void flushBreakup(BreakupItem item, StringBuilder desc) {
        if (item != null && desc != null) {
            item.setDescription(desc.toString().trim());
        }
    }

    private Schedule findScheduleByCode(Tender tender, String code) {
        for (Schedule s : tender.getSchedules()) {
            if (code.equalsIgnoreCase(s.getCode())) {
                return s;
            }
        }
        return null;
    }

    private ScheduleEntry matchEntryByDescription(Schedule schedule, String desc) {
        if (schedule == null || desc == null) {
            return null;
        }
        String key = desc.toLowerCase().replaceAll("\\s+", " ").trim();
        if (key.isEmpty()) {
            return null;
        }
        String probe = key.length() > 25 ? key.substring(0, 25) : key;
        for (ScheduleEntry e : schedule.getEntries()) {
            String ed = e.getDescription() == null ? "" : e.getDescription().toLowerCase().replaceAll("\\s+", " ").trim();
            if (ed.isEmpty()) {
                continue;
            }
            if (ed.equals(key) || ed.startsWith(probe) || key.startsWith(ed.length() > 25 ? ed.substring(0, 25) : ed)) {
                return e;
            }
        }
        return null;
    }

    private ScheduleEntry findEntryBySerial(Schedule schedule, int serial) {
        if (schedule == null) {
            return null;
        }
        for (ScheduleEntry e : schedule.getEntries()) {
            if (e.getSerialNo() != null && e.getSerialNo() == serial) {
                return e;
            }
        }
        return null;
    }

    /**
     * Detects a priced row's trailing "unit qty rate amount". Returns null for heading rows.
     * Uses the last three numeric tokens as qty/rate/amount and the token(s) before as the unit.
     */
    private ParsedTail tryParseTail(String rest) {
        String[] tok = rest.split("\\s+");
        int n = tok.length;
        if (n < 4) {
            return null;
        }
        if (!isNumericToken(tok[n - 1]) || !isNumericToken(tok[n - 2]) || !isNumericToken(tok[n - 3])) {
            return null;
        }
        String amount = tok[n - 1];
        String rate = tok[n - 2];
        String qty = tok[n - 3];
        int unitIdx = n - 4;
        String unit = tok[unitIdx];
        int descEnd = unitIdx;
        if (unitIdx - 1 >= 0 && tok[unitIdx - 1].matches("(?i)per|running")) {
            unit = tok[unitIdx - 1] + " " + unit;
            descEnd = unitIdx - 1;
        }
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < descEnd; i++) {
            if (d.length() > 0) {
                d.append(' ');
            }
            d.append(tok[i]);
        }
        return new ParsedTail(d.toString(), unit, qty, rate, amount);
    }

    private boolean isNumericToken(String t) {
        return P_NUMERIC.matcher(t).matches();
    }

    /**
     * Detects a breakup row. Strips an optional leading serial and an optional code-like token
     * (dotted DSR codes like 4.1.4, or plain numeric item numbers like "1"), then checks for a
     * trailing unit/qty/rate/amount. Returns null for description-continuation lines.
     */
    private BreakupRow detectBreakupRow(String line) {
        String[] tok = line.split("\\s+");
        if (tok.length == 0) return null;
        int i = 0;
        Integer serial = null;
        if (tok.length >= 2 && tok[0].matches("\\d{1,3}")) {
            serial = Integer.valueOf(tok[0]);
            i = 1;
        }
        String code = null;
        if (i < tok.length && looksLikeCode(tok[i])) {
            code = tok[i];
            i++;
        }
        StringBuilder rb = new StringBuilder();
        for (int j = i; j < tok.length; j++) {
            if (rb.length() > 0) rb.append(' ');
            rb.append(tok[j]);
        }
        String rest = rb.toString();
        ParsedTail tail = tryParseTail(rest);
        if (code == null && tail == null) {
            return null; // continuation / non-row line
        }
        return new BreakupRow(serial, code, rest, tail);
    }

    private boolean looksLikeCode(String t) {
        return t.matches("\\d[\\d.]*[A-Z]{0,3}");
    }

    private record BreakupRow(Integer serial, String code, String rest, ParsedTail tail) {
    }

    private record ParsedTail(String desc, String unit, String qty, String rate, String amount) {
    }

    // ---------------- helpers ----------------

    private List<String> stripArtifacts(String[] rawLines) {
        List<String> lines = new ArrayList<>();
        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.equals("TENDER DOCUMENT")) continue;
            if (line.endsWith("DIVISION-ENGINEERING/NORTH WESTERN RLY")) continue;
            if (line.startsWith("Tender No:") && line.contains("Closing Date/Time:")) continue;
            if (P_FOOTER.matcher(line).find()) continue;
            lines.add(line);
        }
        return lines;
    }

    private boolean isJunk(String line) {
        if (line.startsWith("S.No.")) return true;
        return line.matches("(?i)(Above/?|Below/?P?(ar)?|Par|ar|Code|Unit|Bidding|Code Unit)");
    }

    private RateSource rateSourceFromName(String name) {
        String u = name.toUpperCase();
        String compact = u.replaceAll("[^A-Z]", "");
        if (u.contains("N.S.") || compact.contains("NONSCHEDUL") || compact.contains("NSITEMS")) {
            return RateSource.NS;
        }
        if (u.contains("IRUSSOR")) return RateSource.IRUSSOR;
        return RateSource.DSR;
    }

    private String extractEdition(String name) {
        Matcher m = P_EDITION.matcher(name);
        if (!m.find()) {
            return null;
        }
        String digits = m.group(1);
        return digits.length() == 2 ? "20" + digits : digits;
    }

    private String extractPost(String nameOfWork) {
        if (nameOfWork == null) return null;
        Matcher m = P_POST.matcher(nameOfWork);
        return m.find() ? m.group(1) : null;
    }

    private Escalation parseEscalation(String token) {
        String t = token.trim();
        if (t.equalsIgnoreCase("AT Par")) {
            return new Escalation(BigDecimal.ZERO, true);
        }
        boolean negative = t.contains("(-)") || t.startsWith("-");
        BigDecimal value = new BigDecimal(t.replaceAll("[()+\\-\\s]", ""));
        return new Escalation(negative ? value.negate() : value, false);
    }

    private int indexOfLine(List<String> lines, String marker) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(marker)) return i;
        }
        return -1;
    }

    private List<String> safeSub(List<String> lines, int from, int to) {
        if (from < 0) from = 0;
        if (to > lines.size()) to = lines.size();
        if (from >= to) return List.of();
        return lines.subList(from, to);
    }

    private static String capitalizeWords(String s) {
        String[] parts = s.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static BigDecimal parseMoney(String s) {
        if (s == null) return null;
        String c = s.replaceAll("[,\\s]", "");
        if (c.isEmpty()) return null;
        try {
            return new BigDecimal(c);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseIntSafe(String s) {
        if (s == null) return null;
        Matcher m = Pattern.compile("-?\\d+").matcher(s);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), D_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.trim(), D_DATETIME);
        } catch (Exception e) {
            return null;
        }
    }

    /** Escalation parsed from a schedule/NS row. */
    private record Escalation(BigDecimal pct, boolean atPar) {
    }
}
