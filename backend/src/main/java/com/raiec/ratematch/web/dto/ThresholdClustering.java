package com.raiec.ratematch.web.dto;

import java.math.BigDecimal;

/**
 * Evidence that rates may have been set against the tolerance rather than against cost.
 *
 * <p>The vetting rule is public: quote within the warning band and nothing is raised.
 * A rate honestly derived from cost has no reason to land near that line, and across
 * many independent items the variances should scatter. When an unusual share of them
 * instead bunch just underneath a threshold, the most economical explanation is that
 * someone knew where the line was and priced up to it.
 *
 * <p>This is a signal, never a verdict. Small estimates produce clustered-looking numbers
 * by chance, common materials genuinely do sit near their reference rates, and a
 * department applying a standard uplift will look identical to one gaming the band. The
 * fields below therefore describe what was observed and leave the conclusion to the
 * officer — which is also why {@code suspicious} requires a meaningful sample.
 *
 * @param bandStartPct   lower edge of the band examined, just under the fail threshold
 * @param bandEndPct     the fail threshold itself
 * @param itemsInBand    priced, referenced items whose variance falls in that band
 * @param comparedItems  priced, referenced items in total — the denominator
 * @param sharePct       itemsInBand as a share of comparedItems
 * @param valueInBand    what the clustered items are worth as quoted
 * @param excessInBand   how much of that is above reference
 * @param suspicious     whether the concentration is strong enough, on a large enough
 *                       sample, to be worth an officer's attention
 * @param note           a plain-language reading of the numbers above
 */
public record ThresholdClustering(
        BigDecimal bandStartPct,
        BigDecimal bandEndPct,
        int itemsInBand,
        int comparedItems,
        BigDecimal sharePct,
        BigDecimal valueInBand,
        BigDecimal excessInBand,
        boolean suspicious,
        String note
) {
}
