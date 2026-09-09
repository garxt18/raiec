package com.raiec.search.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vocabulary is what lets an officer's words reach the document's words. These tests
 * pin the crossings that matter and, just as importantly, the ones that must not happen:
 * a synonym list that over-reaches turns every search into the whole archive.
 */
class SearchVocabularyTest {

    private static boolean crosses(String from, String to) {
        return SearchVocabulary.expand(from).contains(to);
    }

    @Test
    void abbreviationsReachTheirFullForm() {
        assertTrue(crosses("rcc", "reinforced cement concrete"));
        assertTrue(crosses("fob", "foot over bridge"));
        assertTrue(crosses("aden", "assistant divisional engineer"));
    }

    @Test
    void fullFormsReachTheirAbbreviation() {
        // Symmetry matters: the officer may type either, and the document may contain either.
        assertTrue(crosses("reinforced cement concrete", "rcc"));
        assertTrue(crosses("overhead equipment", "ohe"));
    }

    @Test
    void everydayWordingReachesTheEstimatesWording() {
        // A document says "Earth work in excavation by mechanical means".
        assertTrue(crosses("earthwork", "excavation"));
        assertTrue(crosses("demolition", "dismantling"));
        assertTrue(crosses("tiles", "flooring"));
    }

    @Test
    void aTermInTwoGroupsKeepsBothMeanings() {
        // "concrete" is its own idea and part of RCC; merging must not drop either.
        var expanded = SearchVocabulary.expand("concrete");
        assertTrue(expanded.contains("cement concrete"));
        assertTrue(expanded.size() > 1);
    }

    @Test
    void unrelatedTradesDoNotBleedIntoEachOther() {
        // The failure mode of a synonym list is over-reach: if painting reaches plastering
        // and plastering reaches concrete, every search eventually returns everything.
        assertFalse(crosses("painting", "concrete"));
        assertFalse(crosses("ballast", "brickwork"));
        assertFalse(crosses("laundry", "excavation"));
    }

    @Test
    void anUnknownWordExpandsToItselfAndNothingElse() {
        assertEquals(List.of("kalyanpura"), List.copyOf(SearchVocabulary.expand("kalyanpura")));
    }

    @Test
    void wordsTrueOfEveryTenderAreNotWorthMatchingOn() {
        // Every record here is railway construction work.
        assertTrue(SearchVocabulary.isStopword("railway"));
        assertTrue(SearchVocabulary.isStopword("construction"));
        assertTrue(SearchVocabulary.isStopword("work"));
        assertFalse(SearchVocabulary.isStopword("laundry"), "a real subject must survive");
        assertFalse(SearchVocabulary.isStopword("bikaner"));
    }

    @Test
    void phrasesAreDetectedInTheRawQuery() {
        // So that a typed phrase expands as a phrase; its separate words never reach "rcc".
        assertTrue(SearchVocabulary.phrasesIn("supply of reinforced cement concrete m20")
                .contains("reinforced cement concrete"));
    }

    @Test
    void queryTermsDropNoiseButKeepSubjects() {
        List<String> terms = SearchService.terms(SearchService.normalise("Civil engg works for the laundry at Bikaner"));
        assertTrue(terms.contains("laundry"));
        assertTrue(terms.contains("bikaner"));
        assertFalse(terms.contains("for"));
        assertFalse(terms.contains("works"));
    }

    @Test
    void normalisationStripsPunctuationAndCase() {
        assertEquals("232 25 26", SearchService.normalise("232-25-26"));
        assertEquals("rcc m20 work", SearchService.normalise("  RCC (M20) work!  "));
    }

    @Test
    void genericTradeWordsAreNotTreatedAsEarthwork() {
        // A rate schedule says "including cutting, hoisting, fixing" about steel plate far
        // more often than it means earth cutting. Carrying the bare words made a search for
        // "earthwork" return structural steelwork.
        assertFalse(crosses("earthwork", "cutting"));
        assertFalse(crosses("earthwork", "filling"));
        assertTrue(crosses("earthwork", "excavation"), "the real crossing must survive");
    }
}
