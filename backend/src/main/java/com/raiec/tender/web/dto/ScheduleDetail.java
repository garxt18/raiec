package com.raiec.tender.web.dto;

import com.raiec.tender.entity.Schedule;

import java.math.BigDecimal;
import java.util.List;

/** Full detail of a schedule with its entries. */
public record ScheduleDetail(
        String code,
        String name,
        String rateSource,
        String edition,
        BigDecimal totalAmount,
        String biddingUnit,
        List<ScheduleEntryDetail> entries
) {
    public static ScheduleDetail from(Schedule s) {
        List<ScheduleEntryDetail> entries = s.getEntries().stream()
                .map(ScheduleEntryDetail::from)
                .toList();
        return new ScheduleDetail(
                s.getCode(),
                s.getName(),
                s.getRateSource() != null ? s.getRateSource().name() : null,
                s.getEdition(),
                s.getTotalAmount(),
                s.getBiddingUnit(),
                entries);
    }
}
