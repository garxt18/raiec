package com.raiec.lar.web.dto;

import com.raiec.lar.entity.LarRecord;

import java.math.BigDecimal;
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
        LocalDate approvedOn
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
                r.getApprovedOn());
    }
}
