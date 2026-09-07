package com.raiec.ratematch.web.dto;

import java.math.BigDecimal;

/**
 * One line item's rate-match result. status: OK | WARN | FAIL | NO_REFERENCE.
 *
 * <p>{@code referenceRate} is the rate as published in the rate book (or the previously accepted
 * LAR rate). {@code effectiveReferenceRate} is that rate after the tender's escalation percentage
 * has been applied, which is the rate the vetting officer may actually accept — variance is always
 * measured against the effective rate, never the raw book rate.
 */
public record RateMatchItem(
        String schedule,
        String itemCode,
        String description,
        String source,
        BigDecimal quantity,
        String unit,
        BigDecimal tenderRate,
        BigDecimal amount,
        BigDecimal referenceRate,
        BigDecimal effectiveReferenceRate,
        String referenceSource,
        BigDecimal escalationPct,
        boolean atPar,
        boolean referenceStale,
        BigDecimal variancePct,
        String status
) {
}
