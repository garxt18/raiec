package com.raiec.ratematch.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuzzyMatcherTest {

    @Test
    void identicalAfterNormalizationScoresOne() {
        assertEquals(1.0, FuzzyMatcher.similarity("All kinds of soil.", "all   kinds of SOIL"), 1e-9);
    }

    @Test
    void nearIdenticalWordingMatchesAboveThreshold() {
        double s = FuzzyMatcher.similarity(
                "Earth work in excavation by mechanical means in all kinds of soil",
                "Earth work in excavation by mechanical means over areas in all kinds of soil");
        assertTrue(s >= 0.45, "expected a strong fuzzy match, got " + s);
    }

    @Test
    void unrelatedDescriptionsScoreLow() {
        double s = FuzzyMatcher.similarity(
                "Providing and laying cement concrete 1:2:4",
                "Clearing jungle including uprooting of rank vegetation");
        assertTrue(s < 0.45, "expected a weak match, got " + s);
    }

    @Test
    void emptyOrNullIsZero() {
        assertEquals(0.0, FuzzyMatcher.similarity(null, "anything"), 1e-9);
        assertEquals(0.0, FuzzyMatcher.similarity("", "anything"), 1e-9);
    }
}
