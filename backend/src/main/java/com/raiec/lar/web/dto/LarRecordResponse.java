package com.raiec.lar.web.dto;

import com.raiec.lar.entity.LarRecord;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** API view of a LAR record. */
public record LarRecordResponse(
        Long id,
        String larCode,
        String description,
        String unit,
        BigDecimal rate,
        BigDecimal quantity,
        BigDecimal amount,
        BigDecimal escalationPct,
        String sourceTenderNo,
        String division,
        String sourcePost,
        LocalDate approvedOn,
        /**
         * When the row entered the dataset, to the second. approvedOn carries only a date,
         * which is enough to reason about a rate's age but not enough to tell two entries
         * from the same day apart -- and telling them apart is exactly what someone
         * reviewing the day's additions needs to do.
         */
        Instant recordedAt,
        Instant updatedAt
) {
    public static LarRecordResponse from(LarRecord r) {
        return new LarRecordResponse(
                r.getId(),
                r.getLarCode(),
                r.getDescription(),
                r.getUnit(),
                r.getRate(),
                r.getQuantity(),
                r.getAmount(),
                r.getEscalationPct(),
                r.getSourceTenderNo(),
                r.getDivision(),
                r.getSourcePost(),
                r.getApprovedOn(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
