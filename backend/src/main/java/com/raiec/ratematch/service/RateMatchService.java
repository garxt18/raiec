package com.raiec.ratematch.service;

import com.raiec.lar.entity.LarRecord;
import com.raiec.lar.repository.LarRecordRepository;
import com.raiec.ratematch.web.dto.RateMatchItem;
import com.raiec.ratematch.web.dto.RateMatchResponse;
import com.raiec.reference.entity.DsrItem;
import com.raiec.reference.entity.IrussorItem;
import com.raiec.reference.repository.DsrItemRepository;
import com.raiec.reference.repository.IrussorItemRepository;
import com.raiec.tender.entity.BreakupItem;
import com.raiec.tender.entity.RateSource;
import com.raiec.tender.entity.Schedule;
import com.raiec.tender.entity.ScheduleEntry;
import com.raiec.tender.entity.Tender;
import com.raiec.tender.repository.TenderRepository;
import com.raiec.tender.service.NsItemExtractor;
import com.raiec.tender.service.TenderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Compares each tender line item's quoted rate against a reference:
 *  - DSR / IRUSSOR breakup items -> rate book (dsr_item / irussor_item) by item code + edition,
 *    falling back to a fuzzy (trigram) match on description when the code is not found.
 *  - NS items -> the LAR dataset by description (exact-normalized first, then fuzzy).
 *
 * A tender is NOT matched against its own LAR records (those came from approving this tender),
 * so NS items are vetted only against OTHER tenders' accepted rates. LAR follows "latest AND
 * least": records within {@link #LAR_VALIDITY_MONTHS} months are preferred and the lowest of those
 * wins; an older record is used only when nothing recent matches, and is then flagged as stale.
 *
 * The reference is escalated before comparison. A tenderer quotes one common percentage against
 * the schedule - "AT Par", "(+) 8.50" or "(-) 3.20" - which is loaded onto the book rate to give
 * the rate actually payable, so variance is measured against that effective rate.
 *
 * Variance = (tenderRate - effectiveReferenceRate)/effectiveReferenceRate*100, evaluated
 * directionally: a rate at or below the reference is acceptable (cheaper is good); only
 * over-quoting is flagged (> +10% FAIL, +5..+10% WARN, otherwise OK). Fuzzy matches carry a
 * "~NN%" confidence in the source.
 */
@Service
public class RateMatchService {

    private static final BigDecimal WARN_LIMIT = new BigDecimal("5");
    private static final BigDecimal FAIL_LIMIT = new BigDecimal("10");
    /** Minimum trigram similarity (0..1) for a fuzzy match to be accepted. */
    private static final double FUZZY_THRESHOLD = 0.45;
    /**
     * How long a Last Accepted Rate stays current. The vetting rule is "latest AND least": the
     * benchmark is the lowest rate among recent ones. Older rates are still used (better than no
     * reference at all) but are flagged so the officer knows the comparison may be out of date.
     */
    private static final int LAR_VALIDITY_MONTHS = 12;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final TenderRepository tenderRepository;
    private final DsrItemRepository dsrItemRepository;
    private final IrussorItemRepository irussorItemRepository;
    private final LarRecordRepository larRecordRepository;
    private final NsItemExtractor nsItemExtractor;

    public RateMatchService(TenderRepository tenderRepository,
                            DsrItemRepository dsrItemRepository,
                            IrussorItemRepository irussorItemRepository,
                            LarRecordRepository larRecordRepository,
                            NsItemExtractor nsItemExtractor) {
        this.tenderRepository = tenderRepository;
        this.dsrItemRepository = dsrItemRepository;
        this.irussorItemRepository = irussorItemRepository;
        this.larRecordRepository = larRecordRepository;
        this.nsItemExtractor = nsItemExtractor;
    }

    @Transactional(readOnly = true)
    public RateMatchResponse evaluate(Long tenderId) {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new TenderNotFoundException(tenderId));
        String tenderNo = tender.getTenderNo();

        // LAR records from OTHER tenders, split into those still within the validity window and
        // the full set. Each is keyed by normalized description (lowest rate wins) for fast exact
        // hits, with the list kept for the fuzzy fallback. Recent records are always preferred;
        // an older one is only used when nothing recent matches, and is then flagged as stale.
        LocalDate freshFrom = LocalDate.now().minusMonths(LAR_VALIDITY_MONTHS);
        Map<String, LarRecord> larFreshByDesc = new HashMap<>();
        Map<String, LarRecord> larAnyByDesc = new HashMap<>();
        List<LarRecord> larFresh = new ArrayList<>();
        List<LarRecord> larAny = new ArrayList<>();
        for (LarRecord r : larRecordRepository.findAll()) {
            if (r.getRate() == null) continue;
            if (tenderNo != null && tenderNo.equals(r.getSourceTenderNo())) continue; // exclude self
            String key = normalize(r.getDescription());
            larAny.add(r);
            keepLowest(larAnyByDesc, key, r);
            if (r.getApprovedOn() != null && !r.getApprovedOn().isBefore(freshFrom)) {
                larFresh.add(r);
                keepLowest(larFreshByDesc, key, r);
            }
        }

        // Rate books are loaded lazily, only if a code lookup actually fails and a fuzzy pass is needed.
        List<DsrItem> dsrAll = null;
        List<IrussorItem> irussorAll = null;

        List<RateMatchItem> items = new ArrayList<>();
        for (Schedule schedule : tender.getSchedules()) {
            RateSource src = schedule.getRateSource();
            if (src == RateSource.NS) {
                for (NsItemExtractor.NsLineItem ns : nsItemExtractor.extractFromSchedule(schedule)) {
                    Ref ref = findLarRef(ns.description(), larFreshByDesc, larFresh, larAnyByDesc, larAny);
                    items.add(makeItem(ns.scheduleCode(), ns.itemCode(), ns.description(), "NS",
                            ns.quantity(), ns.unit(), ns.rate(), ns.amount(),
                            ref == null ? null : ref.rate(), ref == null ? null : ref.source(),
                            ns.escalationPct(), ns.atPar(), ref != null && ref.stale()));
                }
            } else {
                for (ScheduleEntry e : schedule.getEntries()) {
                    for (BreakupItem b : e.getBreakupItems()) {
                        if (b.isHeading() || b.getRate() == null) continue;
                        BigDecimal refRate = null;
                        String refSource = null;
                        BookRef br = lookupBook(src, b.getItemCode(), schedule.getEdition());
                        if (br != null) {
                            refRate = br.rate();
                            refSource = br.source();
                        } else if (src == RateSource.DSR) {
                            if (dsrAll == null) dsrAll = dsrItemRepository.findAll();
                            Best fb = bestMatch(b.getDescription(), dsrAll,
                                    DsrItem::getDescription, DsrItem::getRate, i -> "DSR " + i.getEdition());
                            if (fb != null) { refRate = fb.rate(); refSource = fb.source(); }
                        } else if (src == RateSource.IRUSSOR) {
                            if (irussorAll == null) irussorAll = irussorItemRepository.findAll();
                            Best fb = bestMatch(b.getDescription(), irussorAll,
                                    IrussorItem::getDescription, IrussorItem::getRate, i -> "IRUSSOR " + i.getEdition());
                            if (fb != null) { refRate = fb.rate(); refSource = fb.source(); }
                        }
                        items.add(makeItem(schedule.getCode(), b.getItemCode(), b.getDescription(),
                                src != null ? src.name() : null, b.getQuantity(), b.getUnit(),
                                b.getRate(), b.getAmount(), refRate, refSource,
                                e.getEscalationPct(), e.isAtPar(), false));
                    }
                }
            }
        }

        int matched = 0, warn = 0, fail = 0, noRef = 0;
        for (RateMatchItem it : items) {
            switch (it.status()) {
                case "OK" -> matched++;
                case "WARN" -> warn++;
                case "FAIL" -> fail++;
                default -> noRef++;
            }
        }
        return new RateMatchResponse(tenderNo,
                tender.getStatus() != null ? tender.getStatus().name() : null,
                items.size(), matched, warn, fail, noRef, items);
    }

    /**
     * Best fuzzy match in a candidate list above {@link #FUZZY_THRESHOLD}, by trigram similarity
     * on description. Candidates without a usable rate are skipped. The returned source label
     * carries the match confidence (e.g. "DSR 2023 · ~85%").
     */
    private <T> Best bestMatch(String desc, List<T> candidates, Function<T, String> descFn,
                               Function<T, BigDecimal> rateFn, Function<T, String> labelFn) {
        double bestSim = 0;
        T best = null;
        for (T c : candidates) {
            BigDecimal rate = rateFn.apply(c);
            if (rate == null || rate.signum() == 0) continue;
            double s = FuzzyMatcher.similarity(desc, descFn.apply(c));
            if (s > bestSim) {
                bestSim = s;
                best = c;
            }
        }
        if (best == null || bestSim < FUZZY_THRESHOLD) return null;
        String label = labelFn.apply(best) + " · ~" + Math.round(bestSim * 100) + "%";
        return new Best(rateFn.apply(best), label);
    }

    /** Keeps the lowest-rate record per description key. */
    private static void keepLowest(Map<String, LarRecord> map, String key, LarRecord candidate) {
        LarRecord cur = map.get(key);
        if (cur == null || candidate.getRate().compareTo(cur.getRate()) < 0) {
            map.put(key, candidate);
        }
    }

    /**
     * Finds the LAR benchmark for an NS description, preferring recent records over old ones:
     * exact match within the validity window, then fuzzy within the window, then exact outside it,
     * then fuzzy outside it. Anything found outside the window is returned flagged as stale.
     */
    private Ref findLarRef(String desc,
                           Map<String, LarRecord> freshByDesc, List<LarRecord> fresh,
                           Map<String, LarRecord> anyByDesc, List<LarRecord> any) {
        String key = normalize(desc);

        LarRecord exactFresh = freshByDesc.get(key);
        if (exactFresh != null) return new Ref(exactFresh.getRate(), larLabel(exactFresh), false);

        Best fuzzyFresh = bestMatch(desc, fresh,
                LarRecord::getDescription, LarRecord::getRate, RateMatchService::larLabel);
        if (fuzzyFresh != null) return new Ref(fuzzyFresh.rate(), fuzzyFresh.source(), false);

        LarRecord exactOld = anyByDesc.get(key);
        if (exactOld != null) return new Ref(exactOld.getRate(), staleLabel(exactOld), true);

        Best fuzzyOld = bestMatch(desc, any,
                LarRecord::getDescription, LarRecord::getRate, RateMatchService::larLabel);
        if (fuzzyOld != null) return new Ref(fuzzyOld.rate(), fuzzyOld.source() + " · older than "
                + LAR_VALIDITY_MONTHS + " months", true);

        return null;
    }

    private static String larLabel(LarRecord r) {
        return "LAR " + (r.getSourceTenderNo() != null ? r.getSourceTenderNo() : r.getLarCode());
    }

    private static String staleLabel(LarRecord r) {
        return larLabel(r) + " · older than " + LAR_VALIDITY_MONTHS + " months";
    }

    private BookRef lookupBook(RateSource src, String code, String edition) {
        if (code == null) return null;
        if (src == RateSource.DSR) {
            return dsrItemRepository.findByItemCodeAndEdition(code, edition)
                    .map(i -> new BookRef(i.getRate(), "DSR " + i.getEdition()))
                    .orElseGet(() -> dsrItemRepository.findByItemCode(code).stream().findFirst()
                            .map(i -> new BookRef(i.getRate(), "DSR " + i.getEdition())).orElse(null));
        }
        if (src == RateSource.IRUSSOR) {
            return irussorItemRepository.findByItemCodeAndEdition(code, edition)
                    .map(i -> new BookRef(i.getRate(), "IRUSSOR " + i.getEdition()))
                    .orElseGet(() -> irussorItemRepository.findByItemCode(code).stream().findFirst()
                            .map(i -> new BookRef(i.getRate(), "IRUSSOR " + i.getEdition())).orElse(null));
        }
        return null;
    }

    private RateMatchItem makeItem(String schedule, String code, String desc, String source,
                                   BigDecimal qty, String unit, BigDecimal tenderRate, BigDecimal amount,
                                   BigDecimal referenceRate, String referenceSource,
                                   BigDecimal escl, boolean atPar, boolean stale) {
        BigDecimal variance = null;
        String status;
        if (referenceRate == null || referenceRate.signum() == 0) {
            status = "NO_REFERENCE";
        } else {
            variance = tenderRate.subtract(referenceRate)
                    .divide(referenceRate, 6, RoundingMode.HALF_UP)
                    .multiply(HUNDRED)
                    .setScale(2, RoundingMode.HALF_UP);
            // Directional: a rate at or below the reference is acceptable (cheaper is good).
            // Only over-quoting is flagged: > +10% FAIL, +5..+10% WARN, otherwise OK.
            if (variance.compareTo(FAIL_LIMIT) > 0) status = "FAIL";
            else if (variance.compareTo(WARN_LIMIT) > 0) status = "WARN";
            else status = "OK";
        }
        return new RateMatchItem(schedule, code, desc, source, qty, unit, tenderRate, amount,
                referenceRate, referenceSource, escl, atPar, stale, variance, status);
    }

    /**
     * The schedule total after the tender's quoted percentage is applied, i.e. what is actually
     * payable. Escalation belongs at this level and NOT to individual unit rates: in an IREPS
     * tender the item breakup rates sum to the schedule's Basic Value, and the percentage is then
     * applied once to that total to give the Amount. Since the breakup rate and the rate-book rate
     * are both pre-escalation figures, per-item variance compares them directly.
     */
    static BigDecimal applyEscalation(BigDecimal basicValue, BigDecimal escl) {
        if (basicValue == null) return null;
        if (escl == null || escl.signum() == 0) return basicValue;
        BigDecimal factor = BigDecimal.ONE.add(escl.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        return basicValue.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private record BookRef(BigDecimal rate, String source) {
    }

    private record Best(BigDecimal rate, String source) {
    }

    /** A resolved reference rate, with whether it came from outside the LAR validity window. */
    private record Ref(BigDecimal rate, String source, boolean stale) {
    }
}
