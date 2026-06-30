package com.raiec.tender.service;

import com.raiec.lar.entity.LarRecord;
import com.raiec.lar.repository.LarRecordRepository;
import com.raiec.tender.entity.RateSource;
import com.raiec.tender.entity.Schedule;
import com.raiec.tender.entity.ScheduleEntry;
import com.raiec.tender.entity.ScheduleEntryKind;
import com.raiec.tender.entity.Tender;
import com.raiec.tender.entity.TenderStatus;
import com.raiec.tender.ingest.TenderPdfParser;
import com.raiec.tender.repository.TenderRepository;
import com.raiec.tender.web.dto.ApprovalResponse;
import com.raiec.tender.web.dto.RecentActivity;
import com.raiec.tender.web.dto.TenderDetailResponse;
import com.raiec.tender.web.dto.TenderStatsResponse;
import com.raiec.tender.web.dto.TenderSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingests tender PDFs into the database and serves tender summaries.
 */
@Service
public class TenderService {

    private final TenderPdfParser parser;
    private final TenderRepository tenderRepository;
    private final LarRecordRepository larRecordRepository;
    private final NsItemExtractor nsItemExtractor;

    public TenderService(TenderPdfParser parser,
                         TenderRepository tenderRepository,
                         LarRecordRepository larRecordRepository,
                         NsItemExtractor nsItemExtractor) {
        this.parser = parser;
        this.tenderRepository = tenderRepository;
        this.larRecordRepository = larRecordRepository;
        this.nsItemExtractor = nsItemExtractor;
    }

    /** Parses a tender PDF, rejects duplicates, persists the full tree, and returns a summary. */
    @Transactional
    public TenderSummaryResponse ingestPdf(byte[] pdfBytes, String originalFileName) {
        Tender parsed;
        try {
            parsed = parser.parse(pdfBytes);
        } catch (IOException e) {
            throw new TenderParseException("Could not read the PDF file: " + e.getMessage(), e);
        }

        if (parsed.getTenderNo() == null || parsed.getTenderNo().isBlank()) {
            throw new TenderParseException("Could not read a tender number from the PDF.");
        }
        if (tenderRepository.existsByTenderNo(parsed.getTenderNo())) {
            throw new DuplicateTenderException(parsed.getTenderNo());
        }

        parsed.setOriginalFileName(originalFileName);
        Tender saved = tenderRepository.save(parsed);
        return TenderSummaryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TenderSummaryResponse> listAll() {
        return tenderRepository.findAll().stream()
                .map(TenderSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenderDetailResponse getDetail(Long id) {
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new TenderNotFoundException(id));
        return TenderDetailResponse.from(tender);
    }

    @Transactional(readOnly = true)
    public TenderStatsResponse stats() {
        List<Tender> all = tenderRepository.findAll();
        long total = all.size();
        long finalized = all.stream().filter(t -> t.getStatus() == TenderStatus.APPROVED).count();
        long closed = all.stream().filter(t -> t.getStatus() == TenderStatus.REJECTED).count();
        long underReview = all.stream().filter(t -> t.getStatus() == TenderStatus.OFFICER_REVIEW).count();
        long active = all.stream()
                .filter(t -> t.getStatus() != TenderStatus.APPROVED && t.getStatus() != TenderStatus.REJECTED)
                .count();

        List<RecentActivity> recent = all.stream()
                .sorted((a, b) -> {
                    Instant ca = a.getCreatedAt();
                    Instant cb = b.getCreatedAt();
                    if (ca == null && cb == null) return 0;
                    if (ca == null) return 1;
                    if (cb == null) return -1;
                    return cb.compareTo(ca);
                })
                .limit(5)
                .map(t -> new RecentActivity(
                        t.getTenderNo(),
                        t.getNameOfWork(),
                        t.getStatus() != null ? t.getStatus().name() : null,
                        t.getCreatedAt()))
                .toList();

        return new TenderStatsResponse(total, active, underReview, finalized, closed, recent);
    }

    @Transactional
    public void delete(Long id) {
        if (!tenderRepository.existsById(id)) {
            throw new TenderNotFoundException(id);
        }
        tenderRepository.deleteById(id);
    }

    /**
     * Officer approval: pushes every Non-Scheduled item into the LAR dataset keeping the
     * LOWEST accepted rate per (normalized) description, then marks the tender APPROVED.
     *
     * NOTE: "same NS item" is currently matched by normalized exact text. Near-duplicate
     * wording is not merged yet; fuzzy matching (pg_trgm) is a planned upgrade shared with
     * the rate-match engine.
     */
    @Transactional
    public ApprovalResponse approve(Long id) {
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new TenderNotFoundException(id));
        if (tender.getStatus() == TenderStatus.APPROVED) {
            throw new IllegalArgumentException("Tender '" + tender.getTenderNo() + "' is already approved.");
        }

        String prefix = "LAR-" + LocalDate.now().getYear() + "-";
        long seq = larRecordRepository.countByLarCodeStartingWith(prefix);

        // Existing LAR keyed by normalized description, holding the lowest-rate record so far.
        Map<String, LarRecord> lowestByDesc = new HashMap<>();
        for (LarRecord r : larRecordRepository.findAll()) {
            String key = normalize(r.getDescription());
            LarRecord cur = lowestByDesc.get(key);
            if (cur == null || (r.getRate() != null && cur.getRate() != null && r.getRate().compareTo(cur.getRate()) < 0)) {
                lowestByDesc.put(key, r);
            }
        }

        int added = 0;
        int updated = 0;

        for (NsItemExtractor.NsLineItem ns : nsItemExtractor.extract(tender)) {
            if (ns.rate() == null) {
                continue;
            }
            String key = normalize(ns.description());
            LarRecord existing = lowestByDesc.get(key);
            if (existing == null) {
                LarRecord rec = buildLarRecord(ns, tender, prefix + String.format("%05d", ++seq));
                larRecordRepository.save(rec);
                lowestByDesc.put(key, rec);
                added++;
            } else if (ns.rate().compareTo(existing.getRate()) < 0) {
                existing.setRate(ns.rate());
                existing.setUnit(ns.unit());
                existing.setQuantity(ns.quantity());
                existing.setAmount(ns.amount());
                existing.setEscalationPct(ns.escalationPct());
                existing.setSourceTenderNo(tender.getTenderNo());
                existing.setDivision(tender.getDivision());
                existing.setSourcePost(tender.getPost());
                existing.setApprovedOn(LocalDate.now());
                larRecordRepository.save(existing);
                updated++;
            }
            // else: existing rate is already lower or equal - keep it.
        }

        tender.setStatus(TenderStatus.APPROVED);
        tenderRepository.save(tender);
        return new ApprovalResponse(tender.getTenderNo(), tender.getStatus().name(), added, updated);
    }

    @Transactional
    public ApprovalResponse sendToReview(Long id) {
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new TenderNotFoundException(id));
        if (tender.getStatus() != TenderStatus.APPROVED && tender.getStatus() != TenderStatus.REJECTED) {
            tender.setStatus(TenderStatus.OFFICER_REVIEW);
            tenderRepository.save(tender);
        }
        return new ApprovalResponse(tender.getTenderNo(), tender.getStatus().name(), 0, 0);
    }

    @Transactional
    public ApprovalResponse reject(Long id) {
        Tender tender = tenderRepository.findById(id)
                .orElseThrow(() -> new TenderNotFoundException(id));
        tender.setStatus(TenderStatus.REJECTED);
        tenderRepository.save(tender);
        return new ApprovalResponse(tender.getTenderNo(), tender.getStatus().name(), 0, 0);
    }

    private LarRecord buildLarRecord(NsItemExtractor.NsLineItem ns, Tender tender, String larCode) {
        return LarRecord.builder()
                .larCode(larCode)
                .description(ns.description())
                .unit(ns.unit())
                .rate(ns.rate())
                .quantity(ns.quantity())
                .amount(ns.amount())
                .escalationPct(ns.escalationPct())
                .sourceTenderNo(tender.getTenderNo())
                .division(tender.getDivision())
                .sourcePost(tender.getPost())
                .approvedOn(LocalDate.now())
                .build();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
