package com.raiec.ratematch.web.dto;

import java.math.BigDecimal;

/** One line item's rate-match result. status: OK | WARN | FAIL | NO_REFERENCE. */
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
        BigDecimal variancePct,
        String status
) {
}
