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
 * so NS items are vetted only against OTHER tenders' accepted rates.
 *
 * Variance = (tenderRate - referenceRate)/referenceRate*100, evaluated directionally:
 * a rate at or below the reference is acceptable (cheaper is good); only over-quoting is flagged
 * (> +10% FAIL, +5..+10% WARN, otherwise OK). Fuzzy matches carry a "~NN%" confidence in the source.
 */
@Service
public class RateMatchService {

    private static final BigDecimal WARN_LIMIT = new BigDecimal("5");
    private static final BigDecimal FAIL_LIMIT = new BigDecimal("10");
    /** Minimum trigram similarity (0..1) for a fuzzy match to be accepted. */
    private static final double FUZZY_THRESHOLD = 0.45;

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

        // LAR records from OTHER tenders: a map keyed by normalized description (lowest rate wins)
        // for fast exact hits, plus the full list for the fuzzy fallback.
        Map<String, LarRecord> larByDesc = new HashMap<>();
        List<LarRecord> larOther = new ArrayList<>();
        for (LarRecord r : larRecordRepository.findAll()) {
            if (r.getRate() == null) continue;
            if (tenderNo != null && tenderNo.equals(r.getSourceTenderNo())) continue; // exclude self
            larOther.add(r);
            String key = normalize(r.getDescription());
            LarRecord cur = larByDesc.get(key);
            if (cur == null || r.getRate().compareTo(cur.getRate()) < 0) {
                larByDesc.put(key, r);
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
                    BigDecimal refRate = null;
                    String refSource = null;
                    LarRecord exact = larByDesc.get(normalize(ns.description()));
                    if (exact != null) {
                        refRate = exact.getRate();
                        refSource = larLabel(exact);
                    } else {
                        Best fb = bestMatch(ns.description(), larOther,
                                LarRecord::getDescription, LarRecord::getRate, RateMatchService::larLabel);
                        if (fb != null) {
                            refRate = fb.rate();
                            refSource = fb.source();
                        }
                    }
                    items.add(makeItem(ns.scheduleCode(), ns.itemCode(), ns.description(), "NS",
                            ns.quantity(), ns.unit(), ns.rate(), ns.amount(), refRate, refSource,
                            ns.escalationPct(), ns.atPar()));
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
                                e.getEscalationPct(), e.isAtPar()));
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

    private static String larLabel(LarRecord r) {
        return "LAR " + (r.getSourceTenderNo() != null ? r.getSourceTenderNo() : r.getLarCode());
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
                                   BigDecimal escl, boolean atPar) {
        BigDecimal variance = null;
        String status;
        if (referenceRate == null || referenceRate.signum() == 0) {
            status = "NO_REFERENCE";
        } else {
            variance = tenderRate.subtract(referenceRate)
                    .divide(referenceRate, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            // Directional: a rate at or below the reference is acceptable (cheaper is good).
            // Only over-quoting is flagged: > +10% FAIL, +5..+10% WARN, otherwise OK.
            if (variance.compareTo(FAIL_LIMIT) > 0) status = "FAIL";
            else if (variance.compareTo(WARN_LIMIT) > 0) status = "WARN";
            else status = "OK";
        }
        return new RateMatchItem(schedule, code, desc, source, qty, unit, tenderRate, amount,
                referenceRate, referenceSource, escl, atPar, variance, status);
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    private record BookRef(BigDecimal rate, String source) {
    }

    private record Best(BigDecimal rate, String source) {
    }
}
