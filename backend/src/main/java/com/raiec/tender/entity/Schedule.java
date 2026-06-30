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
 * A Section-2 schedule within a tender, e.g. "A - CPWD DSR 2021 Items".
 * {@code rateSource} decides which reference book it is compared against.
 */
@Entity
@Table(name = "schedule", indexes = {
        @Index(name = "idx_schedule_tender", columnList = "tender_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tender_id", nullable = false)
    private Tender tender;

    /** Schedule code: "A", "B", "C". */
    @Column(name = "code", length = 8)
    private String code;

    /** Full schedule name, e.g. "CPWD DSR 2021 Items". */
    @Column(name = "name", length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_source", nullable = false, length = 16)
    private RateSource rateSource;

    /** Rate-book edition referenced, e.g. "2021". */
    @Column(name = "edition", length = 16)
    private String edition;

    @Column(name = "total_amount", precision = 16, scale = 2)
    private BigDecimal totalAmount;

    /** "Above/Below/Par" column. */
    @Column(name = "bidding_unit", length = 32)
    private String biddingUnit;

    @Column(name = "display_order")
    private Integer displayOrder;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ScheduleEntry> entries = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public void addEntry(ScheduleEntry entry) {
        entries.add(entry);
        entry.setSchedule(this);
    }
}
