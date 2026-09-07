package com.raiec.ratematch.web.dto;

import java.math.BigDecimal;

/**
 * One line item's rate-match result. status: OK | WARN | FAIL | NO_REFERENCE.
 *
 * <p>{@code referenceRate} is the rate as published in the rate book (or the previously accepted
 * LAR rate). It is compared directly against the tender's unit rate: both are pre-escalation
 * figures, because the tender's escalation percentage is applied once to the schedule total rather
 * than to individual unit rates. {@code escalationPct} is carried here for display only.
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
        String referenceSource,
        BigDecimal escalationPct,
        boolean atPar,
        boolean referenceStale,
        BigDecimal variancePct,
        String status
) {
}
