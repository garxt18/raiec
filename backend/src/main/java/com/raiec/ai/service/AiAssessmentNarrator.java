package com.raiec.ai.service;

import com.raiec.ai.web.dto.AiAnalysisResponse;
import com.raiec.ai.web.dto.AiCheck;
import com.raiec.ratematch.web.dto.RateMatchItem;
import com.raiec.ratematch.web.dto.RateMatchResponse;

import java.util.Comparator;
import java.util.List;

/**
 * Pure (no Spring, no I/O) logic for the AI assessment: derives a risk level and synthesises a
 * readable narrative from the rule-based analysis. This is the always-available fallback used
 * when no LLM provider is configured, and is unit-tested directly.
 */
final class AiAssessmentNarrator {

    private AiAssessmentNarrator() {
    }

    static String deriveRisk(AiAnalysisResponse ai, RateMatchResponse rm) {
        if ((ai != null && ai.fail() > 0) || (rm != null && rm.fail() > 0)) {
            return "HIGH";
        }
        if ((ai != null && ai.warn() > 0) || (rm != null && rm.warn() > 0)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    static String recommendation(String risk) {
        return switch (risk) {
            case "HIGH" -> "Recommendation: do not accept as-is — return for revision or reject the over-quoted items.";
            case "MEDIUM" -> "Recommendation: approve with conditions — seek clarification on the flagged items first.";
            default -> "Recommendation: no material issues found — safe to approve.";
        };
    }

    static String fallbackSummary(String tenderNo, String nameOfWork,
                                  RateMatchResponse rm, AiAnalysisResponse ai, String risk) {
        StringBuilder sb = new StringBuilder();
        sb.append("Estimate ").append(tenderNo == null ? "(unknown)" : tenderNo);
        if (nameOfWork != null && !nameOfWork.isBlank()) {
            sb.append(" (").append(trim(nameOfWork, 90)).append(")");
        }
        sb.append(" — overall risk ").append(risk).append(". ");

        if (rm != null) {
            sb.append("Rate check: ").append(rm.matched()).append(" of ").append(rm.totalItems())
                    .append(" line items are within tolerance");
            if (rm.warn() > 0) {
                sb.append(", ").append(rm.warn()).append(" are 5-10% over reference");
            }
            if (rm.fail() > 0) {
                sb.append(", ").append(rm.fail()).append(" exceed 10% over reference");
            }
            if (rm.noReference() > 0) {
                sb.append(", ").append(rm.noReference()).append(" have no loaded reference");
            }
            sb.append(". ");

            List<RateMatchItem> over = rm.items() == null ? List.of() : rm.items().stream()
                    .filter(it -> "FAIL".equals(it.status()) || "WARN".equals(it.status()))
                    .filter(it -> it.variancePct() != null)
                    .sorted(Comparator.comparing(RateMatchItem::variancePct).reversed())
                    .limit(3)
                    .toList();
            if (!over.isEmpty()) {
                sb.append("Most material: ");
                for (int i = 0; i < over.size(); i++) {
                    RateMatchItem it = over.get(i);
                    String label = it.description() != null ? it.description() : it.itemCode();
                    sb.append(trim(label, 48)).append(" (+").append(it.variancePct()).append("%)");
                    sb.append(i < over.size() - 1 ? "; " : ". ");
                }
            }
        }

        if (ai != null && ai.checks() != null) {
            for (AiCheck c : ai.checks()) {
                if (!"PASS".equals(c.status())) {
                    sb.append(c.title()).append(": ").append(c.detail()).append(" ");
                }
            }
        }

        sb.append(recommendation(risk));
        return sb.toString();
    }

    private static String trim(String s, int n) {
        s = s == null ? "" : s.trim();
        return s.length() > n ? s.substring(0, n - 1) + "\u2026" : s;
    }
}
