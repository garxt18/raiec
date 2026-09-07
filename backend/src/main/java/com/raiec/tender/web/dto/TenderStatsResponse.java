package com.raiec.tender.web.dto;

import java.util.List;

/** Aggregate counts + recent activity for the dashboard. */
public record TenderStatsResponse(
        long total,
        long active,
        long underReview,
        long infoRequested,
        long finalized,
        long closed,
        List<RecentActivity> recent
) {
}
