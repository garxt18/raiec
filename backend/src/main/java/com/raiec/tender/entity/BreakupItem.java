package com.raiec.tender.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

/**
 * A Section-3 item-breakup row under a DSR/IRUSSOR group entry, e.g. item "4.1.4".
 * Heading rows (4.0, 4.1S, 4.1) carry no qty/rate and have {@code heading=true};
 * priced rows carry unit/qty/rate/amount and are what the rate-match engine uses.
 */
@Entity
@Table(name = "breakup_item", indexes = {
        @Index(name = "idx_breakup_entry", columnList = "schedule_entry_id"),
        @Index(name = "idx_breakup_item_code", columnList = "item_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreakupItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_entry_id", nullable = false)
    private ScheduleEntry scheduleEntry;

    @Column(name = "serial_no")
    private Integer serialNo;

    /** DSR/IRUSSOR item number, e.g. "4.1.4" (or a heading code like "4.0"). */
    @Column(name = "item_code", length = 64)
    private String itemCode;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "quantity", precision = 14, scale = 3)
    private BigDecimal quantity;

    /** Quoted/book rate for this item. Null on heading rows. */
    @Column(name = "rate", precision = 14, scale = 2)
    private BigDecimal rate;

    @Column(name = "amount", precision = 16, scale = 2)
    private BigDecimal amount;

    /** True for category/heading rows (no qty/rate). */
    @Column(name = "is_heading", nullable = false)
    private boolean heading;

    /** Preserves the original row order within the breakup. */
    @Column(name = "display_order")
    private Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
