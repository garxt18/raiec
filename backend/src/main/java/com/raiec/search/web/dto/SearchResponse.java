package com.raiec.search.web.dto;

import java.util.List;

/**
 * The result of one search, with enough about the search itself to explain the outcome.
 *
 * <p>{@code expandedTerms} and {@code searchedTenders} are reported because an empty
 * result is ambiguous otherwise: nothing matched, or nothing exists to match. Those call
 * for opposite responses, and only the search knows which happened.
 *
 * @param query           what was typed
 * @param terms           the query reduced to the words worth matching on
 * @param expandedTerms   what those words were taken to also mean, so a surprising hit
 *                        can be traced back to the vocabulary that produced it
 * @param searchedTenders how many tenders were examined
 * @param hits            matches, best first
 */
public record SearchResponse(
        String query,
        List<String> terms,
        List<String> expandedTerms,
        int searchedTenders,
        List<SearchHit> hits
) {
}
