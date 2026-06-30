package com.raiec.tender.web.dto;

import com.raiec.tender.entity.ScheduleEntry;

import java.math.BigDecimal;
import java.util.List;

/** Full detail of a Section-2 schedule row, with its breakup items. */
public record ScheduleEntryDetail(
        Integer serialNo,
        String kind,
        String itemCode,
        String description,
        BigDecimal quantity,
        String qtyUnit,
        BigDecimal unitRate,
        BigDecimal basicValue,
        BigDecimal escalationPct,
        boolean atPar,
        BigDecimal amount,
        List<BreakupItemDetail> breakupItems
) {
    public static ScheduleEntryDetail from(ScheduleEntry e) {
        List<BreakupItemDetail> items = e.getBreakupItems().stream()
                .map(BreakupItemDetail::from)
                .toList();
        return new ScheduleEntryDetail(
                e.getSerialNo(),
                e.getKind() != null ? e.getKind().name() : null,
                e.getItemCode(),
                e.getDescription(),
                e.getQuantity(),
                e.getQtyUnit(),
                e.getUnitRate(),
                e.getBasicValue(),
                e.getEscalationPct(),
                e.isAtPar(),
                e.getAmount(),
                items);
    }
}
