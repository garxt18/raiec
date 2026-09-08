package com.raiec.tender.web.dto;

import com.raiec.tender.entity.ScheduleEntry;
import com.raiec.tender.entity.Tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API response summarising a tender and its schedules. Built from the entity graph
 * inside a transaction so we never serialise lazy/bidirectional JPA entities directly.
 */
public record TenderSummaryResponse(
        Long id,
        String tenderNo,
        String nameOfWork,
        String division,
        String post,
        BigDecimal advertisedValue,
        LocalDateTime closingDateTime,
        String status,
        String originalFileName,
        int scheduleCount,
        int totalScheduleEntries,
        int totalBreakupItems,
        /** When this submission entered RAIEC. Distinct from the tender's own dates,
            which describe the procurement rather than our record of it. */
        Instant createdAt,
        Instant updatedAt,
        List<ScheduleSummary> schedules
) {

    public static TenderSummaryResponse from(Tender t) {
        List<ScheduleSummary> schedules = t.getSchedules().stream()
                .map(s -> {
                    int breakup = s.getEntries().stream()
                            .mapToInt(e -> e.getBreakupItems().size())
                            .sum();
                    return new ScheduleSummary(
                            s.getCode(),
                            s.getName(),
                            s.getRateSource() != null ? s.getRateSource().name() : null,
                            s.getEdition(),
                            s.getTotalAmount(),
                            s.getEntries().size(),
                            breakup);
                })
                .toList();

        int totalEntries = schedules.stream().mapToInt(ScheduleSummary::entryCount).sum();
        int totalBreakup = schedules.stream().mapToInt(ScheduleSummary::breakupItemCount).sum();

        return new TenderSummaryResponse(
                t.getId(),
                t.getTenderNo(),
                t.getNameOfWork(),
                t.getDivision(),
                t.getPost(),
                t.getAdvertisedValue(),
                t.getClosingDateTime(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getOriginalFileName(),
                schedules.size(),
                totalEntries,
                totalBreakup,
                t.getCreatedAt(),
                t.getUpdatedAt(),
                schedules);
    }
}
