package com.raiec.ratematch.web.dto;

import java.util.List;

/**
 * Rate-match results for one tender: the counts, the money, and the line items.
 *
 * <p>{@code items} is ordered by financial impact, largest excess first. The officer's
 * time is finite, so the item that costs the most should not be somewhere down a list
 * ordered by the sequence it happened to appear in the PDF.
 */
public record RateMatchResponse(
        String tenderNo,
        String status,
        int totalItems,
        int matched,
        int warn,
        int fail,
        int noReference,
        FinancialImpact financials,
        List<RateMatchItem> items
) {
}
