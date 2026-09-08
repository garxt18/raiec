package com.raiec.tender.web.dto;

import com.raiec.tender.entity.TenderEvent;

import java.math.BigDecimal;
import java.time.Instant;

/** One entry in a tender's audit trail. */
public record TenderEventResponse(
        Long id,
        Long tenderId,
        String type,
        String detail,
        String actor,
        BigDecimal excessAtEvent,
        Instant at
) {
    public static TenderEventResponse from(TenderEvent e) {
        return new TenderEventResponse(e.getId(), e.getTenderId(), e.getType(),
                e.getDetail(), e.getActor(), e.getExcessAtEvent(), e.getAt());
    }
}
