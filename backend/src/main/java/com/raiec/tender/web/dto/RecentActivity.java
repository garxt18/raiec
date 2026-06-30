package com.raiec.tender.web.dto;

import java.time.Instant;

/** A recent tender activity entry for the dashboard notifications. */
public record RecentActivity(
        String tenderNo,
        String nameOfWork,
        String status,
        Instant at
) {
}
