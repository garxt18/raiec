package com.raiec.ratematch.web.dto;

import java.util.List;

public record RateMatchResponse(
        String tenderNo,
        String status,
        int totalItems,
        int matched,
        int warn,
        int fail,
        int noReference,
        List<RateMatchItem> items
) {
}
