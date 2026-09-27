package com.raiec.tender.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The accountability picture: who has been deciding, and what it cost.
 *
 * @param officers         one row per person who has decided anything, busiest first
 * @param recentDecisions  the latest decisions across every tender, newest first
 * @param totalApproved    tenders approved by anyone
 * @param totalExcessLet   rupees above reference across all approvals
 * @param unattributed     decisions with no name against them. Should be zero; a non-zero
 *                         figure means something decided a tender without a signed-in
 *                         user, and that is worth investigating rather than hiding.
 */
public record AccountabilityResponse(
        List<OfficerRecord> officers,
        List<TenderEventResponse> recentDecisions,
        int totalApproved,
        BigDecimal totalExcessLet,
        int unattributed
) {
}
