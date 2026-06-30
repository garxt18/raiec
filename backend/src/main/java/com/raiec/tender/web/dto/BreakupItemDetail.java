package com.raiec.tender.web.dto;

import com.raiec.tender.entity.BreakupItem;

import java.math.BigDecimal;

/** Full detail of a Section-3 breakup row. */
public record BreakupItemDetail(
        Integer serialNo,
        String itemCode,
        String description,
        String unit,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal amount,
        boolean heading,
        Integer displayOrder
) {
    public static BreakupItemDetail from(BreakupItem b) {
        return new BreakupItemDetail(
                b.getSerialNo(),
                b.getItemCode(),
                b.getDescription(),
                b.getUnit(),
                b.getQuantity(),
                b.getRate(),
                b.getAmount(),
                b.isHeading(),
                b.getDisplayOrder());
    }
}
