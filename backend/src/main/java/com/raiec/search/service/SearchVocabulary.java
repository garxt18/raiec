package com.raiec.search.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The words a railway estimate uses for the same thing.
 *
 * <p>Searching a tender archive by exact wording fails almost immediately, because the
 * thing an officer remembers and the thing the document says are rarely the same string.
 * A document reads "Earth work in excavation by mechanical means"; the officer types
 * "earthwork". It reads "Reinforced cement concrete"; they type "RCC". A search that
 * cannot cross that gap sends them back to opening tenders one at a time, which is the
 * problem it was supposed to remove.
 *
 * <p>This is a curated vocabulary rather than a learned embedding, and that is a
 * deliberate choice for this application. It needs no model, no network call and no
 * warm-up, it behaves identically on a laptop and on a free-tier server, and — most
 * importantly for a vetting tool — every match it produces can be explained to the person
 * relying on it. An officer can be told "this matched because RCC means reinforced cement
 * concrete", which is a sentence a similarity score cannot produce. The cost is that it
 * only knows the words written here, so it is expected to grow as real estimates surface
 * vocabulary that is missing.
 *
 * <p>Groups are symmetric: any term in a group expands to every other term in it.
 */
final class SearchVocabulary {

    private SearchVocabulary() {
    }

    /**
     * Words too common in this corpus to narrow anything down.
     *
     * <p>Every tender here is railway construction work, so "railway", "work" and "civil"
     * appear in most of them and matching on those returns the archive. Ordinary English
     * stopwords are dropped for the same reason.
     */
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "an", "and", "the", "of", "for", "in", "at", "to", "by", "on", "with",
            "from", "under", "over", "as", "is", "are", "be", "or", "its", "his", "her",
            "their", "this", "that", "these", "those", "into", "out", "per", "etc",
            // Domain-wide terms: true of nearly every record, so useless as a filter.
            "work", "works", "railway", "railways", "tender", "estimate", "civil",
            "construction", "provision", "providing", "provide", "supply", "supplying",
            "including", "misc", "miscellaneous", "various", "connection", "nwr"
    ));

    private static final List<List<String>> GROUPS = List.of(
            // ---- earthwork and foundations ----
            // "cutting" and "filling" are deliberately absent as bare words. They are
            // earthwork terms, but a rate schedule uses them far more often for cutting
            // steel plate and filling joints, so including them made a search for
            // "earthwork" match structural steelwork. The qualified phrases are safe.
            List.of("earthwork", "earth work", "excavation", "excavating", "banking",
                    "earth cutting", "earth filling"),
            List.of("foundation", "footing"),
            List.of("dismantling", "demolition", "demolishing", "removal", "dismantle"),

            // ---- concrete and steel ----
            List.of("rcc", "reinforced cement concrete", "reinforced concrete"),
            List.of("pcc", "plain cement concrete"),
            List.of("cc", "cement concrete", "concrete"),
            List.of("reinforcement", "rebar", "tor steel", "tmt", "steel bar", "binding steel"),
            List.of("shuttering", "formwork", "centering", "staging"),
            List.of("structural steel", "girder", "joist", "truss"),

            // ---- masonry and finishes ----
            List.of("brickwork", "brick work", "brick masonry", "masonry", "brick"),
            List.of("plaster", "plastering", "rendering"),
            List.of("flooring", "floor", "tiling", "tiles", "kota", "vitrified"),
            List.of("painting", "paint", "whitewash", "white wash", "distemper", "enamel"),
            List.of("waterproofing", "water proofing", "damp proofing", "dpc"),

            // ---- roads and track ----
            List.of("road", "pavement", "wbm", "wmm", "carriageway"),
            List.of("bitumen", "bituminous", "asphalt", "premix"),
            List.of("ballast", "track ballast", "stone ballast"),
            List.of("sleeper", "sleepers", "pst", "prestressed sleeper"),
            List.of("track", "permanent way", "p way", "pway", "rail"),

            // ---- structures ----
            List.of("culvert", "box culvert", "hume pipe", "npc pipe"),
            List.of("bridge", "rob", "rub", "road over bridge", "road under bridge"),
            List.of("footbridge", "fob", "foot over bridge", "foot bridge"),
            List.of("platform", "pf", "platform surface"),
            List.of("boundary wall", "compound wall", "fencing", "parapet"),
            List.of("shed", "canopy", "roofing", "roof", "sheeting"),

            // ---- buildings and amenities ----
            List.of("quarters", "quarter", "staff quarters", "housing", "residential"),
            List.of("station building", "booking office", "waiting hall"),
            List.of("laundry", "washing", "mechanised laundry", "mechanized laundry"),
            List.of("toilet", "lavatory", "urinal", "sanitary", "wc"),
            List.of("water supply", "plumbing", "pipeline", "water line"),
            List.of("drain", "drainage", "sewer", "sewerage", "nala"),

            // ---- services ----
            List.of("electrification", "ohe", "overhead equipment", "traction"),
            List.of("electrical", "wiring", "lighting", "illumination", "lights"),
            List.of("signalling", "signaling", "signal", "telecom"),
            List.of("lift", "elevator", "escalator"),

            // ---- who filed it ----
            List.of("aden", "assistant divisional engineer"),
            List.of("sse", "senior section engineer"),
            List.of("dyce", "dy ce", "deputy chief engineer"),
            List.of("drm", "divisional railway manager"),

            // ---- infrastructure programmes ----
            List.of("augmentation", "upgradation", "upgrade", "improvement", "strengthening"),
            List.of("maintenance", "repair", "repairs", "overhaul", "renovation"),
            List.of("amenities", "passenger amenities", "facilities", "facility")
    );

    /** term -> every term that means the same thing, including itself. */
    private static final Map<String, Set<String>> SYNONYMS = buildIndex();

    private static Map<String, Set<String>> buildIndex() {
        Map<String, Set<String>> index = new HashMap<>();
        for (List<String> group : GROUPS) {
            for (String term : group) {
                // A term can legitimately appear in more than one group ("concrete" is both
                // its own idea and part of RCC), so groups are merged rather than replaced.
                index.computeIfAbsent(term, k -> new LinkedHashSet<>()).addAll(group);
            }
        }
        return index;
    }

    static boolean isStopword(String token) {
        return STOPWORDS.contains(token);
    }

    /**
     * Every phrasing of one query term, the term itself first.
     *
     * <p>Multi-word entries are returned whole. The caller matches them as substrings of
     * normalised text, so "reinforced cement concrete" matches the document's own phrasing
     * without the individual words having to be searched separately.
     */
    static Set<String> expand(String term) {
        Set<String> out = new LinkedHashSet<>();
        out.add(term);
        Set<String> known = SYNONYMS.get(term);
        if (known != null) out.addAll(known);
        return out;
    }

    /**
     * Multi-word vocabulary entries present in the raw query, so that a phrase the user
     * typed is expanded as a phrase and not only as its separate words.
     */
    static Set<String> phrasesIn(String normalisedQuery) {
        Set<String> found = new LinkedHashSet<>();
        for (String term : SYNONYMS.keySet()) {
            if (term.indexOf(' ') >= 0 && normalisedQuery.contains(term)) found.add(term);
        }
        return found;
    }
}
