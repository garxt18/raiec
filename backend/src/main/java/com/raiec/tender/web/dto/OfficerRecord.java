package com.raiec.tender.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * What one person has decided, and what those decisions were worth.
 *
 * <p>The manipulation risk in the problem statement is that a vetting officer can favour a
 * tender filed by someone they know. No single approval proves that, and this record does
 * not try to: what it does is make the pattern visible. An officer who approves everything
 * put in front of them, or who approves estimates carrying far more excess than their
 * colleagues do, is answerable to a question they were never previously asked because
 * nobody could see the shape of their decisions.
 *
 * @param actor             the username the decisions were recorded against
 * @param approved          tenders they approved
 * @param rejected          tenders they rejected
 * @param infoRequested     tenders they sent back for clarification
 * @param excessApproved    rupees above reference in the estimates they approved, as
 *                          measured at the moment each decision was taken
 * @param largestApproved   the single largest excess they let through
 * @param lastDecisionAt    when they last decided anything
 */
public record OfficerRecord(
        String actor,
        int approved,
        int rejected,
        int infoRequested,
        BigDecimal excessApproved,
        BigDecimal largestApproved,
        Instant lastDecisionAt
) {
    /** Decisions of every kind, the denominator for "how often do they approve". */
    public int totalDecisions() {
        return approved + rejected + infoRequested;
    }
}
