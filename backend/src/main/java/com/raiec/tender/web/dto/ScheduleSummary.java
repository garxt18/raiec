package com.raiec.tender.web.dto;

import java.math.BigDecimal;

/** Compact summary of one schedule for API responses. */
public record ScheduleSummary(
        String code,
        String name,
        String rateSource,
        String edition,
        BigDecimal totalAmount,
        int entryCount,
        int breakupItemCount
) {
}
