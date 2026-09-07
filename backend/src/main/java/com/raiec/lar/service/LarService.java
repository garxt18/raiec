package com.raiec.lar.service;

import com.raiec.lar.entity.LarRecord;
import com.raiec.lar.repository.LarRecordRepository;
import com.raiec.lar.web.dto.LarRecordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class LarService {

    private final LarRecordRepository larRecordRepository;

    public LarService(LarRecordRepository larRecordRepository) {
        this.larRecordRepository = larRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<LarRecordResponse> listAll() {
        return larRecordRepository.findAll().stream()
                .map(LarRecordResponse::from)
                .toList();
    }

    /**
     * Adds a rate by hand, for work accepted outside RAIEC — the dataset would otherwise
     * only ever know about tenders that happened to pass through this system.
     *
     * <p>Follows the same rule as approval: lowest rate wins. If the description already
     * exists at an equal or lower rate the existing record stands, because a benchmark
     * that could be raised by hand would not be a benchmark. A genuinely lower rate
     * updates the existing entry rather than creating a second row for the same item.
     */
    @Transactional
    public LarRecordResponse addManual(String description, String unit, BigDecimal rate,
                                       BigDecimal quantity, String division, String sourcePost,
                                       String sourceTenderNo, LocalDate approvedOn) {
        if (description == null || description.trim().length() < 5) {
            throw new IllegalArgumentException(
                    "Give a description of at least 5 characters - it is what future tenders are matched against.");
        }
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("Rate must be greater than zero.");
        }
        if (approvedOn != null && approvedOn.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("A rate cannot be accepted in the future.");
        }

        String desc = description.trim();
        String key = normalize(desc);
        LocalDate accepted = approvedOn != null ? approvedOn : LocalDate.now();

        LarRecord existing = larRecordRepository.findAll().stream()
                .filter(r -> normalize(r.getDescription()).equals(key))
                .min((a, b) -> a.getRate().compareTo(b.getRate()))
                .orElse(null);

        if (existing != null && existing.getRate().compareTo(rate) <= 0) {
            throw new IllegalArgumentException(
                    "A lower or equal rate of " + existing.getRate() + " is already recorded for this item ("
                    + existing.getLarCode() + "). The dataset keeps the lowest accepted rate.");
        }

        if (existing != null) {
            existing.setRate(rate);
            existing.setUnit(unit);
            existing.setQuantity(quantity);
            existing.setDivision(division);
            existing.setSourcePost(sourcePost);
            existing.setSourceTenderNo(sourceTenderNo);
            existing.setApprovedOn(accepted);
            return LarRecordResponse.from(larRecordRepository.save(existing));
        }

        String prefix = "LAR-" + LocalDate.now().getYear() + "-";
        long seq = larRecordRepository.countByLarCodeStartingWith(prefix) + 1;

        LarRecord rec = LarRecord.builder()
                .larCode(prefix + String.format("%05d", seq))
                .description(desc)
                .unit(unit)
                .rate(rate)
                .quantity(quantity)
                .division(division)
                .sourcePost(sourcePost)
                .sourceTenderNo(sourceTenderNo)
                .approvedOn(accepted)
                .build();
        return LarRecordResponse.from(larRecordRepository.save(rec));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
