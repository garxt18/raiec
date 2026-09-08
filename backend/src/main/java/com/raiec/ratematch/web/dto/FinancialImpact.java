package com.raiec.ratematch.web.dto;

import java.math.BigDecimal;

/**
 * What the variances are worth in rupees.
 *
 * <p>Counting flagged items answers "how many things look wrong", which is not the
 * question a vetting officer is actually asked. Three items at +40% on ₹8,000 of work
 * matter less than one item at +6% on ₹2 crore, but a count ranks them the other way
 * round. These figures put the flags in proportion to the money.
 *
 * @param quotedValue        total value of every priced line item in the estimate
 * @param comparableValue    the part of that which has a reference rate, and is therefore
 *                           the only part these numbers describe
 * @param referenceValue     what the comparable part would cost at reference rates
 * @param excessTotal        sum of the amounts quoted ABOVE reference
 * @param savingTotal        sum of the amounts quoted BELOW reference
 * @param netImpact          excess minus saving: the estimate's overall position against
 *                           the references. Negative means it is cheaper overall.
 * @param coveragePct        share of quoted value that could be checked at all. Without
 *                           this the other totals invite a false conclusion: "₹0 excess"
 *                           reads as clean when it may only mean nothing was checkable.
 * @param unreferencedValue  value with no reference rate, which nobody has vetted
 */
public record FinancialImpact(
        BigDecimal quotedValue,
        BigDecimal comparableValue,
        BigDecimal referenceValue,
        BigDecimal excessTotal,
        BigDecimal savingTotal,
        BigDecimal netImpact,
        BigDecimal coveragePct,
        BigDecimal unreferencedValue
) {
}
