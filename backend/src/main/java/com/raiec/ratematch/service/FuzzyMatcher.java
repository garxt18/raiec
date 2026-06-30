package com.raiec.ratematch.service;

import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight fuzzy text matcher using trigram (3-gram) Jaccard similarity.
 * Returns a score in [0,1]; 1.0 means (normalized) identical text.
 * Used as a fallback when exact item-code / exact-description matching fails.
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {
    }

    public static double similarity(String a, String b) {
        Set<String> ta = trigrams(a);
        Set<String> tb = trigrams(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0;
        }
        int intersection = 0;
        for (String t : ta) {
            if (tb.contains(t)) {
                intersection++;
            }
        }
        int union = ta.size() + tb.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    static Set<String> trigrams(String s) {
        String n = normalize(s);
        Set<String> set = new HashSet<>();
        if (n.isEmpty()) {
            return set;
        }
        String padded = "  " + n + " ";
        for (int i = 0; i + 3 <= padded.length(); i++) {
            set.add(padded.substring(i, i + 3));
        }
        return set;
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }
}
