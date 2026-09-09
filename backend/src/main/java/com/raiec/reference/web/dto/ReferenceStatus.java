package com.raiec.reference.web.dto;

/**
 * Whether the system has anything to check estimates against.
 *
 * <p>Rate matching is only as meaningful as the books behind it. With IRUSSOR and DSR
 * empty, every scheduled item comes back "no reference", the excess totals to zero, and
 * the interface reports a clean estimate — a conclusion it has no basis for. The
 * per-tender coverage figure catches this after the fact; this reports it beforehand, so
 * an officer is told the tool is not ready rather than discovering it through a result
 * that looks like good news.
 *
 * @param dsrItems      priced rows loaded from the CPWD Delhi Schedule of Rates
 * @param irussorItems  priced rows loaded from IRUSSOR
 * @param larRecords    Last Accepted Rates recorded from approved tenders
 * @param ready         whether at least one published rate book is loaded. LAR alone does
 *                      not qualify: it only covers Non-Scheduled items, so scheduled work
 *                      would still go unchecked.
 * @param note          a plain-language statement of what is missing and what follows
 */
public record ReferenceStatus(
        long dsrItems,
        long irussorItems,
        long larRecords,
        boolean ready,
        String note
) {
}
