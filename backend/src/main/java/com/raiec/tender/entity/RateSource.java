package com.raiec.tender.entity;

/**
 * Which rate reference a schedule is compared against.
 * DSR -> dsr_item, IRUSSOR -> irussor_item, NS -> lar_record (by description).
 */
public enum RateSource {
    DSR,
    IRUSSOR,
    NS
}
