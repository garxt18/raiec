package com.raiec.tender.service;

import com.raiec.tender.entity.BreakupItem;
import com.raiec.tender.entity.RateSource;
import com.raiec.tender.entity.Schedule;
import com.raiec.tender.entity.ScheduleEntry;
import com.raiec.tender.entity.ScheduleEntryKind;
import com.raiec.tender.entity.Tender;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for "what is an NS line item" so LAR extraction and rate matching
 * always agree. Handles both NS formats:
 *   - inline NS rows (e.g. "NS01 ... rate ...")            -> one item
 *   - group-style NS items with an item breakup            -> one item per priced breakup row
 *     (falls back to the group lump if it has no breakup)
 */
@Component
public class NsItemExtractor {

    public record NsLineItem(String scheduleCode, String itemCode, String description, String unit,
                             BigDecimal quantity, BigDecimal rate, BigDecimal amount,
                             BigDecimal escalationPct, boolean atPar) {
    }

    public List<NsLineItem> extract(Tender tender) {
        List<NsLineItem> out = new ArrayList<>();
        for (Schedule s : tender.getSchedules()) {
            if (s.getRateSource() == RateSource.NS) {
                out.addAll(extractFromSchedule(s));
            }
        }
        return out;
    }

    public List<NsLineItem> extractFromSchedule(Schedule s) {
        List<NsLineItem> out = new ArrayList<>();
        for (ScheduleEntry e : s.getEntries()) {
            if (e.getKind() == ScheduleEntryKind.NS_ITEM) {
                BigDecimal rate = e.getUnitRate() != null ? e.getUnitRate() : e.getAmount();
                if (rate == null) continue;
                out.add(new NsLineItem(s.getCode(), e.getItemCode(), e.getDescription(), e.getQtyUnit(),
                        e.getQuantity(), rate, e.getAmount(), e.getEscalationPct(), e.isAtPar()));
            } else {
                List<BreakupItem> priced = e.getBreakupItems().stream()
                        .filter(b -> !b.isHeading() && b.getRate() != null).toList();
                if (priced.isEmpty()) {
                    if (e.getAmount() != null) {
                        out.add(new NsLineItem(s.getCode(), e.getItemCode(), e.getDescription(), e.getQtyUnit(),
                                e.getQuantity(), e.getAmount(), e.getAmount(), e.getEscalationPct(), e.isAtPar()));
                    }
                } else {
                    for (BreakupItem b : priced) {
                        out.add(new NsLineItem(s.getCode(), b.getItemCode(), b.getDescription(), b.getUnit(),
                                b.getQuantity(), b.getRate(), b.getAmount(), e.getEscalationPct(), e.isAtPar()));
                    }
                }
            }
        }
        return out;
    }
}
