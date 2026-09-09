package com.raiec.search;

import com.raiec.search.service.SearchService;
import com.raiec.search.web.dto.SearchHit;
import com.raiec.search.web.dto.SearchResponse;
import com.raiec.tender.service.TenderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Search exists to answer "have we tendered something like this before". These tests pin
 * the behaviours that make it able to: finding work described inside the document rather
 * than only in its title, crossing the gap between how an officer says something and how
 * the estimate writes it, and saying why each result matched.
 */
@SpringBootTest
@Transactional
class SearchIntegrationTest {

    private static final Path PDF = Paths.get("..", "sample-tenders", "viewNitPdf_5207353.pdf");

    @Autowired
    private TenderService tenderService;
    @Autowired
    private SearchService searchService;

    private void loadSample() throws Exception {
        assumeTrue(Files.exists(PDF), "sample PDF not present locally");
        tenderService.ingestPdf(Files.readAllBytes(PDF), "232-25-26.pdf");
    }

    @Test
    void findsATenderByItsNumber() throws Exception {
        loadSample();
        SearchResponse r = searchService.search("232-25-26", 10);
        assertFalse(r.hits().isEmpty(), "the reference number must still find its tender");
        assertEquals("232-25-26", r.hits().get(0).tenderNo());
    }

    @Test
    void findsATenderByWhatTheWorkIs() throws Exception {
        loadSample();
        // The officer remembers the work, not the number. That is the whole point.
        SearchResponse r = searchService.search("laundry", 10);
        assertFalse(r.hits().isEmpty(), "should find the mechanised laundry tender by subject");
    }

    @Test
    void findsWorkMentionedOnlyInTheLineItems() throws Exception {
        loadSample();
        // "earthwork" is not in this tender's title; it is a line item inside the schedule.
        // Searching only titles would miss it, and missing it is what sends an officer back
        // to opening tenders one at a time.
        SearchResponse r = searchService.search("earthwork", 10);
        assertFalse(r.hits().isEmpty(), "should reach into the line items");
        assertTrue(r.hits().get(0).reasons().stream()
                        .anyMatch(x -> x.field().toLowerCase().contains("line item")),
                "the hit should say it came from a line item: " + r.hits().get(0).reasons());
    }

    @Test
    void everyHitExplainsItself() throws Exception {
        loadSample();
        SearchResponse r = searchService.search("concrete", 10);
        for (SearchHit h : r.hits()) {
            assertFalse(h.reasons().isEmpty(), "a result with no stated reason looks like a bug");
            for (SearchHit.MatchReason reason : h.reasons()) {
                assertFalse(reason.field().isBlank());
                assertFalse(reason.snippet().isBlank());
            }
        }
    }

    @Test
    void matchingMoreOfTheQueryRanksHigher() throws Exception {
        loadSample();
        SearchResponse both = searchService.search("laundry Bikaner", 10);
        SearchResponse one = searchService.search("laundry Guwahati", 10);
        assumeTrue(!both.hits().isEmpty() && !one.hits().isEmpty());
        // Same tender, but the query it answers more completely should score higher.
        assertTrue(both.hits().get(0).score() > one.hits().get(0).score(),
                "covering both terms should outrank covering one");
    }

    @Test
    void aQueryOfOnlyCommonWordsReturnsNothingRatherThanEverything() throws Exception {
        loadSample();
        // "railway construction work" is true of every record here. Returning the archive
        // ranked arbitrarily would look like a search that works while telling you nothing.
        SearchResponse r = searchService.search("railway construction work", 10);
        assertTrue(r.hits().isEmpty(), "matched " + r.hits().size() + " on stopwords alone");
        assertTrue(r.terms().isEmpty());
    }

    @Test
    void nonsenseFindsNothing() throws Exception {
        loadSample();
        assertTrue(searchService.search("zzzqqqxyz", 10).hits().isEmpty());
    }

    @Test
    void anEmptyQueryIsNotAWildcard() throws Exception {
        loadSample();
        assertTrue(searchService.search("   ", 10).hits().isEmpty());
    }

    @Test
    void theResponseSaysHowManyTendersItLookedAt() throws Exception {
        loadSample();
        // An empty result is ambiguous: nothing matched, or nothing exists. These two call
        // for opposite responses from the officer, so the count is reported either way.
        SearchResponse r = searchService.search("zzzqqqxyz", 10);
        assertTrue(r.searchedTenders() >= 1, "should report the corpus it searched");
    }

    @Test
    void limitIsRespected() throws Exception {
        loadSample();
        List<SearchHit> hits = searchService.search("concrete", 1).hits();
        assertTrue(hits.size() <= 1);
    }
}
