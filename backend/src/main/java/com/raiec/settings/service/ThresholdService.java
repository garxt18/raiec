package com.raiec.settings.service;

import com.raiec.settings.entity.RateThresholds;
import com.raiec.settings.repository.RateThresholdsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Reads and updates the department-wide tolerance bands.
 *
 * <p>Every rate comparison asks for these, so the row is read on each evaluation rather than
 * cached: a threshold change must take effect immediately, and one primary-key lookup per
 * tender evaluation is not worth the staleness risk of a cache.
 */
@Service
public class ThresholdService {

    private final RateThresholdsRepository repository;

    public ThresholdService(RateThresholdsRepository repository) {
        this.repository = repository;
    }

    /** Current settings, seeding the defaults the first time they are requested. */
    @Transactional
    public RateThresholds current() {
        return repository.findById(RateThresholds.SINGLETON_ID)
                .orElseGet(() -> repository.save(RateThresholds.defaults()));
    }

    /**
     * Applies an update. Only non-null fields are changed, so a caller can adjust one band
     * without restating the others.
     *
     * @throws IllegalArgumentException if the values would not form a usable set
     */
    @Transactional
    public RateThresholds update(BigDecimal warnPct, BigDecimal failPct,
                                 Integer larValidityMonths, BigDecimal fuzzyThreshold,
                                 String updatedBy) {
        RateThresholds t = current();

        if (warnPct != null) t.setWarnPct(warnPct);
        if (failPct != null) t.setFailPct(failPct);
        if (larValidityMonths != null) t.setLarValidityMonths(larValidityMonths);
        if (fuzzyThreshold != null) t.setFuzzyThreshold(fuzzyThreshold);

        validate(t);
        t.setUpdatedBy(updatedBy);
        return repository.save(t);
    }

    private void validate(RateThresholds t) {
        if (t.getWarnPct().signum() < 0 || t.getFailPct().signum() < 0) {
            throw new IllegalArgumentException("Tolerance percentages cannot be negative.");
        }
        if (t.getWarnPct().compareTo(t.getFailPct()) >= 0) {
            throw new IllegalArgumentException(
                    "The WARN threshold must be below the FAIL threshold, otherwise no item could ever be a warning.");
        }
        if (t.getFailPct().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("A FAIL threshold above 100% would flag almost nothing.");
        }
        if (t.getLarValidityMonths() < 1 || t.getLarValidityMonths() > 120) {
            throw new IllegalArgumentException("LAR validity must be between 1 and 120 months.");
        }
        if (t.getFuzzyThreshold().compareTo(BigDecimal.ZERO) <= 0
                || t.getFuzzyThreshold().compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Fuzzy threshold must be between 0 and 1 (exclusive).");
        }
    }
}
