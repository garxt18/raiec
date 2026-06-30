package com.raiec.tender.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A Section-2 schedule row. For DSR/IRUSSOR it is a chapter GROUP (with child BreakupItems);
 * for Non-Scheduled it is a complete NS_ITEM (qty/unit/rate present, no breakup).
 * Escalation lives here in both cases.
 */
@Entity
@Table(name = "schedule_entry", indexes = {
        @Index(name = "idx_entry_schedule", columnList = "schedule_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name = "serial_no")
    private Integer serialNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private ScheduleEntryKind kind;

    /** "NS 1" for NS items; null for DSR/IRUSSOR groups. */
    @Column(name = "item_code", length = 64)
    private String itemCode;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Quantity - NS items only. */
    @Column(name = "quantity", precision = 14, scale = 3)
    private BigDecimal quantity;

    /** Quantity unit - NS items only. */
    @Column(name = "qty_unit", length = 32)
    private String qtyUnit;

    /** Quoted unit rate - NS items only. */
    @Column(name = "unit_rate", precision = 14, scale = 2)
    private BigDecimal unitRate;

    @Column(name = "basic_value", precision = 16, scale = 2)
    private BigDecimal basicValue;

    /** Escalation percentage; 0 when AT Par. */
    @Column(name = "escalation_pct", precision = 6, scale = 2)
    private BigDecimal escalationPct;

    /** True when the row is "AT Par" (no escalation applied). */
    @Column(name = "at_par", nullable = false)
    private boolean atPar;

    @Column(name = "amount", precision = 16, scale = 2)
    private BigDecimal amount;

    @OneToMany(mappedBy = "scheduleEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BreakupItem> breakupItems = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Defensive clamp so a mis-parsed long token (e.g. a description landing in the unit field)
     * can never overflow the column and abort ingestion of an otherwise-good tender.
     */
    @PrePersist
    @PreUpdate
    private void clampFieldLengths() {
        if (qtyUnit != null && qtyUnit.length() > 32) {
            qtyUnit = qtyUnit.substring(0, 32);
        }
        if (itemCode != null && itemCode.length() > 64) {
            itemCode = itemCode.substring(0, 64);
        }
    }

    public void addBreakupItem(BreakupItem item) {
        breakupItems.add(item);
        item.setScheduleEntry(this);
    }
}
