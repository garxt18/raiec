package com.raiec.tender.service;

import com.raiec.tender.entity.TenderEvent;
import com.raiec.tender.repository.TenderEventRepository;
import com.raiec.tender.web.dto.AccountabilityResponse;
import com.raiec.tender.web.dto.OfficerRecord;
import com.raiec.tender.web.dto.TenderEventResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Writes and reads the tender audit trail.
 *
 * <p>Recording is deliberately forgiving: a failure to write the trail must never fail
 * the action it was describing. An officer's approval being rejected because the log
 * could not be written would be a worse outcome than an incomplete log, and the log's
 * gaps are at least visible.
 */
@Service
public class TenderEventService {

    private final TenderEventRepository repository;

    public TenderEventService(TenderEventRepository repository) {
        this.repository = repository;
    }

    public static final String UPLOADED = "UPLOADED";
    public static final String EXTRACTED = "EXTRACTED";
    public static final String RATE_MATCHED = "RATE_MATCHED";
    public static final String AI_ANALYSED = "AI_ANALYSED";
    public static final String SENT_TO_REVIEW = "SENT_TO_REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String INFO_REQUESTED = "INFO_REQUESTED";

    @Transactional
    public void record(Long tenderId, String type, String detail) {
        record(tenderId, type, detail, null);
    }

    @Transactional
    public void record(Long tenderId, String type, String detail, BigDecimal excessAtEvent) {
        if (tenderId == null) return;
        try {
            repository.save(TenderEvent.builder()
                    .tenderId(tenderId)
                    .type(type)
                    .detail(detail)
                    .actor(currentActor())
                    .excessAtEvent(excessAtEvent)
                    .build());
        } catch (RuntimeException ignored) {
            // See the class note: the trail must not be able to break the workflow.
        }
    }

    /**
     * Records unless this step's own most recent entry already says the same thing.
     *
     * <p>Rate matching and analysis re-run whenever their page is opened, and a trail that
     * says "rate match completed" nine times in a row buries the entries that represent an
     * actual decision. A re-run that produces a different result is still recorded, because
     * a changed outcome is exactly what someone reading the trail needs to see.
     *
     * <p>The comparison is against the last event <em>of the same type</em>, not the last
     * event overall. Walking forward and back through the workflow interleaves the steps —
     * rate match, analysis, sent to review, then rate match again — so comparing against
     * whatever happened last would let an unchanged result back in whenever any other step
     * ran in between, which is the flooding this exists to prevent.
     */
    @Transactional
    public void recordOnce(Long tenderId, String type, String detail, BigDecimal excessAtEvent) {
        if (tenderId == null) return;
        try {
            TenderEvent last = null;
            for (TenderEvent e : repository.findByTenderIdOrderByAtAsc(tenderId)) {
                if (type.equals(e.getType())) last = e;      // ordered ascending: keep the newest
            }
            if (last != null && java.util.Objects.equals(detail, last.getDetail())) return;
        } catch (RuntimeException ignored) {
            // Fall through and record: a duplicate entry beats a missing one.
        }
        record(tenderId, type, detail, excessAtEvent);
    }

    @Transactional(readOnly = true)
    public List<TenderEventResponse> forTender(Long tenderId) {
        return repository.findByTenderIdOrderByAtAsc(tenderId).stream()
                .map(TenderEventResponse::from)
                .toList();
    }

    /**
     * Every recorded rate-match result, newest first, so callers can pick out the latest
     * per tender. Deliberately not limited: the dashboard rollup must see one entry for
     * each pending tender, and a fixed window would silently drop the older ones from
     * the total.
     */
    @Transactional(readOnly = true)
    public List<TenderEventResponse> recentRateMatches() {
        return repository.findByTypeOrderByAtDesc(RATE_MATCHED).stream()
                .map(TenderEventResponse::from)
                .toList();
    }

    /** The decision types, as opposed to the pipeline steps that happen on their own. */
    private static final java.util.Set<String> DECISIONS =
            java.util.Set.of(APPROVED, REJECTED, INFO_REQUESTED);

    /**
     * Groups every recorded decision by the person who made it.
     *
     * <p>See {@link com.raiec.tender.web.dto.OfficerRecord} for why this exists. The excess
     * figures come from the rate-match entry that preceded each approval on the same
     * tender, because an APPROVED row carries no excess of its own — the officer's decision
     * is judged against the number that was on their screen at the time, which is exactly
     * the number that entry recorded.
     */
    @Transactional(readOnly = true)
    public AccountabilityResponse accountability() {
        List<TenderEvent> all = repository.findAll();
        all.sort(java.util.Comparator.comparing(TenderEvent::getAt,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));

        // Latest excess seen per tender, walking forward so each decision is paired with
        // the most recent evaluation before it rather than with a later one.
        Map<Long, BigDecimal> excessSoFar = new java.util.HashMap<>();
        Map<String, int[]> counts = new java.util.LinkedHashMap<>();       // [approved, rejected, info]
        Map<String, BigDecimal[]> money = new java.util.HashMap<>();       // [totalExcess, largest]
        Map<String, java.time.Instant> lastAt = new java.util.HashMap<>();

        int totalApproved = 0, unattributed = 0;
        BigDecimal totalExcessLet = BigDecimal.ZERO;

        for (TenderEvent e : all) {
            if (RATE_MATCHED.equals(e.getType()) && e.getExcessAtEvent() != null) {
                excessSoFar.put(e.getTenderId(), e.getExcessAtEvent());
                continue;
            }
            if (!DECISIONS.contains(e.getType())) continue;

            String actor = e.getActor();
            if (actor == null || actor.isBlank() || "system".equals(actor)) {
                unattributed++;
                actor = "unattributed";
            }

            int[] c = counts.computeIfAbsent(actor, k -> new int[3]);
            switch (e.getType()) {
                case APPROVED -> c[0]++;
                case REJECTED -> c[1]++;
                default -> c[2]++;
            }

            if (APPROVED.equals(e.getType())) {
                totalApproved++;
                BigDecimal excess = excessSoFar.getOrDefault(e.getTenderId(), BigDecimal.ZERO);
                totalExcessLet = totalExcessLet.add(excess);
                BigDecimal[] m = money.computeIfAbsent(actor, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                m[0] = m[0].add(excess);
                if (excess.compareTo(m[1]) > 0) m[1] = excess;
            }
            if (e.getAt() != null) {
                java.time.Instant prev = lastAt.get(actor);
                if (prev == null || e.getAt().isAfter(prev)) lastAt.put(actor, e.getAt());
            }
        }

        List<OfficerRecord> officers = counts.entrySet().stream()
                .map(en -> {
                    BigDecimal[] m = money.getOrDefault(en.getKey(), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    int[] c = en.getValue();
                    return new OfficerRecord(en.getKey(), c[0], c[1], c[2], m[0], m[1], lastAt.get(en.getKey()));
                })
                .sorted(java.util.Comparator.comparingInt(OfficerRecord::totalDecisions).reversed())
                .toList();

        List<TenderEventResponse> recent = all.stream()
                .filter(e -> DECISIONS.contains(e.getType()))
                .sorted(java.util.Comparator.comparing(TenderEvent::getAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(25)
                .map(TenderEventResponse::from)
                .toList();

        return new AccountabilityResponse(officers, recent, totalApproved, totalExcessLet, unattributed);
    }

    @Transactional(readOnly = true)
    public List<TenderEventResponse> recent() {
        return repository.findTop20ByOrderByAtDesc().stream()
                .map(TenderEventResponse::from)
                .toList();
    }

    @Transactional
    public void deleteForTender(Long tenderId) {
        repository.deleteByTenderId(tenderId);
    }

    /**
     * Whoever is behind the current request. Steps the pipeline runs on its own account
     * are attributed to "system" rather than to the person who happened to trigger them,
     * so that the trail distinguishes a machine result from a human decision.
     */
    private String currentActor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName() == null || "anonymousUser".equals(a.getName())) return "system";
        return a.getName();
    }
}
