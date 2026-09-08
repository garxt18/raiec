package com.raiec.tender.web.dto;

import java.math.BigDecimal;

/**
 * The money at stake across every tender still awaiting a decision.
 *
 * <p>Per-tender figures answer "should I approve this one". This answers "where should the
 * office spend the week", which is a different and largely unasked question: a queue of
 * forty tenders has a shape, and without a rollup the only way to see it is to open all
 * forty.
 *
 * <p>The figures come from each tender's most recent recorded evaluation rather than from
 * re-running rate matching on demand. That keeps the dashboard cheap to load, and it means
 * the numbers are the ones officers were actually shown. The cost is that a tender nobody
 * has evaluated contributes nothing, which is why {@code notEvaluated} is reported
 * alongside — an unexamined tender is missing from the total, not absent from the risk.
 *
 * @param excessUnderReview  total rupees above reference across pending tenders
 * @param evaluated          pending tenders that have been rate-matched at least once
 * @param notEvaluated       pending tenders never rate-matched, whose exposure is unknown
 * @param withExcess         pending tenders carrying some excess
 * @param largestTenderNo    the pending tender carrying the most excess, if any
 * @param largestExcess      how much that one carries
 */
public record PortfolioImpact(
        BigDecimal excessUnderReview,
        int evaluated,
        int notEvaluated,
        int withExcess,
        String largestTenderNo,
        BigDecimal largestExcess
) {
}
