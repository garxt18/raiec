package com.raiec.auth.entity;

/**
 * Who a user is, in the terms the railway itself uses.
 *
 * <p>The three roles mirror the three real parties to a tender, and the separation is the
 * point rather than a convenience. A tender is filed by the Construction department and
 * vetted by Finance; those are different people answering to different chains, and the
 * whole value of the vetting step rests on the second not being the first. Until now the
 * application had only two roles, so whoever uploaded an estimate could also approve it —
 * which quietly removed the separation the process exists to enforce.
 *
 * <p>Each role is a superset of the one before it in what it may read, and strictly
 * narrower in what it may decide.
 */
public enum Role {

    /**
     * Construction department. Files estimates and follows their progress, and may not
     * decide on them — not even on the ones they did not file, since the filing department
     * has an interest in the outcome either way.
     */
    FILER,

    /**
     * Finance department: the vetting officer this application is built to assist.
     * Approves, rejects, or asks the filing department for clarification. Every such
     * decision is recorded against their name.
     */
    OFFICER,

    /**
     * Administrator. Everything an officer may do, plus the settings that apply
     * department-wide: tolerance thresholds, rate-book imports, hand-entered LAR rates,
     * and the user accounts themselves.
     */
    ADMIN
}
