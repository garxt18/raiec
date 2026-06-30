package com.raiec.tender.entity;

/**
 * Kind of a Section-2 schedule row.
 * GROUP   - a DSR/IRUSSOR chapter group (has child BreakupItems; escalation at this level).
 * NS_ITEM - a Non-Scheduled item, complete in itself (qty/unit/rate present; no breakup).
 */
public enum ScheduleEntryKind {
    GROUP,
    NS_ITEM
}
