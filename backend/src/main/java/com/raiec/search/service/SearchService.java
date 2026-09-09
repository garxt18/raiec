package com.raiec.search.service;

import com.raiec.ratematch.service.FuzzyMatcher;
import com.raiec.search.web.dto.SearchHit;
import com.raiec.search.web.dto.SearchResponse;
import com.raiec.tender.entity.BreakupItem;
import com.raiec.tender.entity.Schedule;
import com.raiec.tender.entity.ScheduleEntry;
import com.raiec.tender.entity.Tender;
import com.raiec.tender.repository.TenderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds tenders by what the work is, not only by its reference number.
 *
 * <p>The home page previously matched a substring against the tender number and returned
 * the first hit. That only helps someone who already knows the number, which is the case
 * where they least need help. The question actually being asked is "have we tendered
 * something like this before, and what did we pay" — and answering it means searching the
 * text of the work, including the line items inside the document, since the item an
 * officer remembers is frequently not mentioned in the title at all.
 *
 * <h2>Scale</h2>
 * <p>Matching happens in memory over every tender and its line items. At this
 * installation's size — tenders in the tens, each with a few hundred items — that is a
 * few milliseconds and buys exact control over ranking and the ability to say why each
 * result matched. It would need to become a database-side index (Postgres full-text, or
 * pg_trgm which is already a dependency of the rate matcher) somewhere in the low
 * thousands of tenders; {@link #MAX_TENDERS_SCANNED} bounds the damage until then.
 */
@Service
public class SearchService {

    private final TenderRepository tenderRepository;

    public SearchService(TenderRepository tenderRepository) {
        this.tenderRepository = tenderRepository;
    }

    /** Beyond this the in-memory approach stops being defensible. See the class note. */
    private static final int MAX_TENDERS_SCANNED = 2000;

    /** Line items examined per tender, so one enormous estimate cannot dominate a request. */
    private static final int MAX_ITEMS_PER_TENDER = 1500;

    /**
     * Field weights. The title says what the tender is *for* and is the strongest signal
     * short of the number itself; a line item says only that the work contains this
     * somewhere, which is a real but weaker answer to "have we done this before".
     */
    private static final double W_TENDER_NO = 12.0;
    private static final double W_NAME = 5.0;
    private static final double W_PLACE = 2.5;    // division, post, tendering section
    private static final double W_ITEM = 2.0;

    /** Below this trigram similarity a "close" word is noise rather than a typo. */
    private static final double FUZZY_FLOOR = 0.62;

    private static final int MAX_REASONS = 4;

    @Transactional(readOnly = true)
    public SearchResponse search(String rawQuery, int limit) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        List<Tender> all = tenderRepository.findAll();
        int scanned = Math.min(all.size(), MAX_TENDERS_SCANNED);

        if (query.isEmpty()) {
            return new SearchResponse(query, List.of(), List.of(), scanned, List.of());
        }

        String normalised = normalise(query);
        List<String> terms = terms(normalised);
        if (terms.isEmpty()) {
            // Everything typed was a stopword ("the works"). Saying so beats returning the
            // whole archive ranked arbitrarily.
            return new SearchResponse(query, List.of(), List.of(), scanned, List.of());
        }

        // Each term carries its own expansions, so a hit can be attributed to the term the
        // officer actually typed rather than to whichever synonym happened to match.
        List<Set<String>> expansions = new ArrayList<>();
        Set<String> allExpanded = new LinkedHashSet<>();
        for (String t : terms) {
            Set<String> e = SearchVocabulary.expand(t);
            expansions.add(e);
            allExpanded.addAll(e);
        }
        // A phrase the user typed is expanded as a phrase too: "reinforced cement concrete"
        // should reach "RCC", which none of its individual words do.
        for (String phrase : SearchVocabulary.phrasesIn(normalised)) {
            Set<String> e = SearchVocabulary.expand(phrase);
            terms.add(phrase);
            expansions.add(e);
            allExpanded.addAll(e);
        }

        List<SearchHit> hits = new ArrayList<>();
        for (Tender t : all.subList(0, scanned)) {
            SearchHit hit = score(t, terms, expansions, normalised);
            if (hit != null) hits.add(hit);
        }

        hits.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        if (hits.size() > limit) hits = hits.subList(0, limit);

        return new SearchResponse(query, List.copyOf(terms), List.copyOf(allExpanded), scanned, hits);
    }

    private SearchHit score(Tender t, List<String> terms, List<Set<String>> expansions, String normalisedQuery) {
        double score = 0;
        List<SearchHit.MatchReason> reasons = new ArrayList<>();
        Set<String> matchedTerms = new LinkedHashSet<>();

        String tenderNo = normalise(t.getTenderNo());
        String name = normalise(t.getNameOfWork());
        String place = normalise(join(t.getDivision(), t.getPost(), t.getTenderingSection()));

        // The reference number, handled apart from the vocabulary: it is an identifier, so
        // a containment test is right and synonyms are meaningless.
        if (!tenderNo.isEmpty() && (tenderNo.contains(normalisedQuery) || normalisedQuery.contains(tenderNo))) {
            score += W_TENDER_NO;
            reasons.add(new SearchHit.MatchReason("Tender number", t.getTenderNo()));
            matchedTerms.add(t.getTenderNo());
        }

        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            Set<String> variants = expansions.get(i);

            String inName = firstHit(name, variants);
            if (inName != null) {
                score += W_NAME;
                matchedTerms.add(term);
                addReason(reasons, "Name of work", snippet(t.getNameOfWork(), inName));
                continue;      // the strongest evidence for this term is already recorded
            }

            String inPlace = firstHit(place, variants);
            if (inPlace != null) {
                score += W_PLACE;
                matchedTerms.add(term);
                addReason(reasons, "Division / office", join(t.getDivision(), t.getPost()));
                continue;
            }

            if (isDimensionLike(term)) continue;   // see isDimensionLike: "25" is not a subject

            ItemHit item = findInItems(t, variants);
            if (item != null) {
                score += W_ITEM;
                matchedTerms.add(term);
                addReason(reasons, "Line item", snippet(item.text, item.matched));
                continue;
            }

            // Nothing contained the term. Allow for it being mistyped, but only against the
            // title: running fuzzy comparisons across every line item of every tender costs
            // far more than it finds.
            if (fuzzyHitsTitle(name, term)) {
                score += W_NAME * 0.6;    // a probable match should not outrank a certain one
                matchedTerms.add(term);
                addReason(reasons, "Name of work (close match)", t.getNameOfWork());
            }
        }

        if (score <= 0) return null;

        // Reward covering the whole query. Two tenders each matching "laundry" rank equally
        // on raw hits, but the one that also matches "Bikaner" is the one being looked for.
        double coverage = (double) matchedTerms.size() / Math.max(terms.size(), 1);
        score *= (0.5 + 0.5 * coverage);

        return new SearchHit(
                t.getId(), t.getTenderNo(), t.getNameOfWork(), t.getDivision(), t.getPost(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getAdvertisedValue(), t.getCreatedAt(),
                round(score), List.copyOf(reasons), List.copyOf(matchedTerms));
    }

    private record ItemHit(String text, String matched) {
    }

    private ItemHit findInItems(Tender t, Set<String> variants) {
        int examined = 0;
        for (Schedule s : t.getSchedules()) {
            for (ScheduleEntry e : s.getEntries()) {
                if (++examined > MAX_ITEMS_PER_TENDER) return null;
                String hit = firstHit(normalise(e.getDescription()), variants);
                if (hit != null) return new ItemHit(e.getDescription(), hit);

                for (BreakupItem b : e.getBreakupItems()) {
                    if (++examined > MAX_ITEMS_PER_TENDER) return null;
                    // Heading rows carry category names rather than work, and matching them
                    // produces hits an officer cannot act on.
                    if (b.isHeading()) continue;
                    String bh = firstHit(normalise(b.getDescription()), variants);
                    if (bh != null) return new ItemHit(b.getDescription(), bh);
                }
            }
        }
        return null;
    }

    /**
     * The first variant present in the text as a whole word, or null.
     *
     * <p>Whole-word, not substring. Plain containment looked correct until the vocabulary
     * contained short terms: "rail" then matched every "railway", and "cc" matched
     * "success" and "occasional". Both texts are already normalised to single-spaced
     * lowercase, so padding each side is enough to anchor the ends without a regex, and it
     * works for multi-word phrases unchanged.
     */
    private static String firstHit(String text, Set<String> variants) {
        if (text == null || text.isEmpty()) return null;
        String padded = " " + text + " ";
        for (String v : variants) {
            if (padded.contains(" " + v + " ")) return v;
        }
        return null;
    }

    /**
     * Whether a term is a bare number short enough to be a dimension rather than a subject.
     *
     * <p>Searching "232-25-26" splits into 232, 25 and 26, and a rate schedule is full of
     * "25 mm thick". Those hits are real but meaningless, and they clutter the reasons on a
     * result that already matched on its number. Such fragments are still allowed to match
     * the tender number itself, which is where they belong.
     */
    private static boolean isDimensionLike(String term) {
        if (term.length() > 3) return false;
        for (int i = 0; i < term.length(); i++) {
            if (!Character.isDigit(term.charAt(i))) return false;
        }
        return true;
    }

    /**
     * Whether any single word of the title is close enough to the term to be a typo.
     * Compared word by word: a short term is always dissimilar to a long title taken whole,
     * so comparing against the whole string would never fire.
     */
    private static boolean fuzzyHitsTitle(String name, String term) {
        if (name.isEmpty() || term.length() < 4) return false;   // short words mistype into real ones
        for (String word : name.split("\\s+")) {
            if (word.length() < 4) continue;
            if (FuzzyMatcher.similarity(word, term) >= FUZZY_FLOOR) return true;
        }
        return false;
    }

    /**
     * Records one reason per field.
     *
     * <p>A two-word query whose words both appear in the title produced two reasons both
     * labelled "Name of work", which reads as a rendering fault rather than as extra
     * information. One reason per field says where the match came from, which is what the
     * list is for; how completely the query was covered is carried by
     * {@link SearchHit#matchedTerms()} instead.
     */
    private static void addReason(List<SearchHit.MatchReason> reasons, String field, String snippet) {
        if (reasons.size() >= MAX_REASONS) return;
        for (SearchHit.MatchReason r : reasons) {
            if (r.field().equals(field)) return;
        }
        reasons.add(new SearchHit.MatchReason(field, snippet));
    }

    /**
     * The matching region of a long description rather than its first hundred characters,
     * which in a rate schedule are boilerplate and identical across items.
     */
    private static String snippet(String original, String matchedTerm) {
        if (original == null) return "";
        String hay = original.toLowerCase(Locale.ROOT);
        int at = hay.indexOf(matchedTerm);
        if (at < 0) return trim(original, 140);

        int from = Math.max(0, at - 45);
        int to = Math.min(original.length(), at + matchedTerm.length() + 95);
        String cut = original.substring(from, to).trim();
        return (from > 0 ? "… " : "") + cut + (to < original.length() ? " …" : "");
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p);
        }
        return sb.toString();
    }

    /** Lowercase, punctuation to spaces, whitespace collapsed. */
    static String normalise(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** The query reduced to words worth matching on. */
    static List<String> terms(String normalised) {
        List<String> out = new ArrayList<>();
        for (String w : normalised.split(" ")) {
            if (w.isBlank() || w.length() < 2) continue;
            if (SearchVocabulary.isStopword(w)) continue;
            if (!out.contains(w)) out.add(w);
        }
        return out;
    }

    private static double round(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
