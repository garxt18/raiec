package com.raiec.search.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One tender that matched, and the evidence for why it did.
 *
 * <p>{@code reasons} exists because a hit is often not self-explaining. A search for
 * "earthwork" can return a tender whose title is about a laundry building, because the
 * match came from a line item three levels inside the document. Without saying so, the
 * result looks like a bug and the officer stops trusting the search. With it, the result
 * reads as the search doing exactly its job.
 *
 * @param matchedTerms which of the query's terms this tender actually accounted for,
 *                     so a partial match is visibly partial rather than silently ranked
 */
public record SearchHit(
        Long id,
        String tenderNo,
        String nameOfWork,
        String division,
        String post,
        String status,
        BigDecimal advertisedValue,
        Instant createdAt,
        double score,
        List<MatchReason> reasons,
        List<String> matchedTerms
) {
    /**
     * Where a match came from.
     *
     * @param field   the part of the tender that matched, in words an officer would use
     * @param snippet the matching text itself, trimmed to the region around the hit
     */
    public record MatchReason(String field, String snippet) {
    }
}
