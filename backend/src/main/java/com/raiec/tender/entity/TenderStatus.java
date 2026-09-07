package com.raiec.tender.entity;

/**
 * Pipeline state of a tender within RAIEC (mirrors the frontend 5-step flow, not IREPS).
 */
public enum TenderStatus {
    UPLOADED,
    OCR_EXTRACTED,
    RATE_MATCHED,
    AI_ANALYZED,
    OFFICER_REVIEW,
    /** Officer has asked the filing department for clarification; not yet decided. */
    INFO_REQUESTED,
    APPROVED,
    REJECTED
}
