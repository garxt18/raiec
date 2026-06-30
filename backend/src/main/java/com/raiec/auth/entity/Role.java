package com.raiec.auth.entity;

/**
 * Application roles. ADMIN can manage reference data (rate-book imports); OFFICER (and ADMIN)
 * can approve/reject tenders. Both can view and upload.
 */
public enum Role {
    ADMIN,
    OFFICER
}
