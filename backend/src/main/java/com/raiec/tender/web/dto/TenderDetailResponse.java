package com.raiec.tender.web.dto;

import com.raiec.tender.entity.Tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Full tender detail: header + schedules + entries + breakup items. */
public record TenderDetailResponse(
        Long id,
        String tenderNo,
        String nameOfWork,
        String division,
        String post,
        String tenderingSection,
        BigDecimal advertisedValue,
        BigDecimal earnestMoney,
        BigDecimal tenderDocCost,
        String contractType,
        String contractCategory,
        String expenditureType,
        String biddingType,
        String tenderType,
        String biddingSystem,
        String biddingStyle,
        String periodOfCompletion,
        Integer validityOfOfferDays,
        String rankingOrder,
        LocalDateTime uploadingDateTime,
        LocalDateTime closingDateTime,
        LocalDate biddingStartDate,
        String status,
        String officerRemark,
        String originalFileName,
        /** When RAIEC received this submission, as opposed to the tender's own dates. */
        Instant createdAt,
        Instant updatedAt,
        List<ScheduleDetail> schedules
) {
    public static TenderDetailResponse from(Tender t) {
        List<ScheduleDetail> schedules = t.getSchedules().stream()
                .map(ScheduleDetail::from)
                .toList();
        return new TenderDetailResponse(
                t.getId(),
                t.getTenderNo(),
                t.getNameOfWork(),
                t.getDivision(),
                t.getPost(),
                t.getTenderingSection(),
                t.getAdvertisedValue(),
                t.getEarnestMoney(),
                t.getTenderDocCost(),
                t.getContractType(),
                t.getContractCategory(),
                t.getExpenditureType(),
                t.getBiddingType(),
                t.getTenderType(),
                t.getBiddingSystem(),
                t.getBiddingStyle(),
                t.getPeriodOfCompletion(),
                t.getValidityOfOfferDays(),
                t.getRankingOrder(),
                t.getUploadingDateTime(),
                t.getClosingDateTime(),
                t.getBiddingStartDate(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getOfficerRemark(),
                t.getOriginalFileName(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                schedules);
    }
}
