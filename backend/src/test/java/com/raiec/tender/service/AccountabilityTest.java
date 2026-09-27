package com.raiec.tender.service;

import com.raiec.tender.web.dto.AccountabilityResponse;
import com.raiec.tender.web.dto.OfficerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Turning the audit trail into an answer to "who has been deciding, and what did it cost".
 *
 * <p>The manipulation risk in the problem statement is a vetting officer favouring a
 * familiar filer. No single approval evidences that, so what these tests pin is that the
 * <em>pattern</em> is visible and correctly attributed.
 */
@SpringBootTest
@Transactional
class AccountabilityTest {

    @Autowired
    private TenderEventService events;

    private void actingAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    private OfficerRecord find(AccountabilityResponse r, String actor) {
        return r.officers().stream().filter(o -> o.actor().equals(actor)).findFirst().orElseThrow();
    }

    @Test
    void decisionsAreAttributedToWhoeverMadeThem() {
        actingAs("finance.asha");
        events.record(9001L, TenderEventService.APPROVED, "Estimate approved");
        events.record(9002L, TenderEventService.REJECTED, "Estimate rejected");
        actingAs("finance.ravi");
        events.record(9003L, TenderEventService.APPROVED, "Estimate approved");

        AccountabilityResponse r = events.accountability();
        assertEquals(1, find(r, "finance.asha").approved());
        assertEquals(1, find(r, "finance.asha").rejected());
        assertEquals(1, find(r, "finance.ravi").approved());
        assertEquals(0, find(r, "finance.ravi").rejected());
    }

    @Test
    void anApprovalCarriesTheExcessTheOfficerWasShown() {
        actingAs("finance.asha");
        // The rate match is what put a number on the officer's screen; the approval that
        // follows is the decision taken against that number.
        events.record(9101L, TenderEventService.RATE_MATCHED, "Rate match completed", new BigDecimal("250000.00"));
        events.record(9101L, TenderEventService.APPROVED, "Estimate approved");

        assertEquals(0, new BigDecimal("250000.00").compareTo(find(events.accountability(), "finance.asha").excessApproved()));
    }

    @Test
    void aLaterEvaluationDoesNotRewriteAnEarlierDecision() {
        actingAs("finance.asha");
        events.record(9201L, TenderEventService.RATE_MATCHED, "Rate match completed", new BigDecimal("100000.00"));
        events.record(9201L, TenderEventService.APPROVED, "Estimate approved");
        // Re-running the match afterwards, e.g. after the thresholds changed.
        events.record(9201L, TenderEventService.RATE_MATCHED, "Rate match completed again", new BigDecimal("900000.00"));

        // The officer is answerable for what they were shown, not for what the tender
        // would score today.
        assertEquals(0, new BigDecimal("100000.00").compareTo(find(events.accountability(), "finance.asha").excessApproved()));
    }

    @Test
    void excessAccumulatesAcrossApprovalsAndKeepsTheLargest() {
        actingAs("finance.ravi");
        events.record(9301L, TenderEventService.RATE_MATCHED, "rm", new BigDecimal("40000.00"));
        events.record(9301L, TenderEventService.APPROVED, "approved");
        events.record(9302L, TenderEventService.RATE_MATCHED, "rm", new BigDecimal("310000.00"));
        events.record(9302L, TenderEventService.APPROVED, "approved");

        OfficerRecord o = find(events.accountability(), "finance.ravi");
        assertEquals(0, new BigDecimal("350000.00").compareTo(o.excessApproved()));
        assertEquals(0, new BigDecimal("310000.00").compareTo(o.largestApproved()));
    }

    @Test
    void rejectionsCarryNoExcessBecauseNothingWasLetThrough() {
        actingAs("finance.asha");
        events.record(9401L, TenderEventService.RATE_MATCHED, "rm", new BigDecimal("500000.00"));
        events.record(9401L, TenderEventService.REJECTED, "rejected");

        OfficerRecord o = find(events.accountability(), "finance.asha");
        assertEquals(1, o.rejected());
        assertEquals(0, BigDecimal.ZERO.compareTo(o.excessApproved()));
    }

    @Test
    void pipelineStepsAreNotCountedAsDecisions() {
        actingAs("finance.asha");
        events.record(9501L, TenderEventService.UPLOADED, "uploaded");
        events.record(9501L, TenderEventService.EXTRACTED, "extracted");
        events.record(9501L, TenderEventService.AI_ANALYSED, "analysed");
        events.record(9501L, TenderEventService.SENT_TO_REVIEW, "sent");

        // Opening pages is not deciding anything, so this person does not appear in the
        // list at all rather than appearing with a row of zeroes.
        assertTrue(events.accountability().officers().stream()
                        .noneMatch(o -> o.actor().equals("finance.asha")),
                "someone who only ran the pipeline is not a decision-maker");
    }

    @Test
    void aDecisionWithNoNameAgainstItIsCountedAndNotHidden() {
        SecurityContextHolder.clearContext();   // no signed-in user => recorded as "system"
        events.record(9601L, TenderEventService.APPROVED, "Estimate approved");

        AccountabilityResponse r = events.accountability();
        assertTrue(r.unattributed() >= 1, "an unattributed decision must be surfaced, not swallowed");
        assertEquals(1, find(r, "unattributed").approved());
    }

    @Test
    void recentDecisionsExcludeRoutinePipelineEntries() {
        actingAs("finance.asha");
        events.record(9701L, TenderEventService.RATE_MATCHED, "rm", new BigDecimal("1000"));
        events.record(9701L, TenderEventService.APPROVED, "Estimate approved");

        assertTrue(events.accountability().recentDecisions().stream()
                        .allMatch(e -> List.of(TenderEventService.APPROVED, TenderEventService.REJECTED,
                                TenderEventService.INFO_REQUESTED).contains(e.type())),
                "the feed is about decisions, not about the pipeline running");
    }

    @Test
    void theBusiestDeciderIsListedFirst() {
        actingAs("finance.busy");
        for (int i = 0; i < 4; i++) events.record(9800L + i, TenderEventService.APPROVED, "approved");
        actingAs("finance.quiet");
        events.record(9899L, TenderEventService.APPROVED, "approved");

        assertEquals("finance.busy", events.accountability().officers().get(0).actor());
    }
}
