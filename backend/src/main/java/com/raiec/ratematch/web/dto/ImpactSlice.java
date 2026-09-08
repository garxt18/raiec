package com.raiec.ratematch.web.dto;

import java.math.BigDecimal;

/**
 * Excess attributed to one grouping — a schedule, or a reference source.
 *
 * <p>A single total for the whole estimate says how much is wrong but not where, and
 * "where" is what determines who gets asked about it. Excess concentrated in one schedule
 * is a question for that part of the work; excess spread evenly across every source is a
 * different problem entirely.
 *
 * @param label     the schedule name or reference source this slice covers
 * @param itemCount how many priced lines fall in it
 * @param quotedValue what those lines are worth as quoted
 * @param excessTotal how much of that is above reference
 * @param sharePct  this slice's share of the estimate's total excess, so a slice can be
 *                  read without holding the overall figure in mind
 */
public record ImpactSlice(
        String label,
        int itemCount,
        BigDecimal quotedValue,
        BigDecimal excessTotal,
        BigDecimal sharePct
) {
}
