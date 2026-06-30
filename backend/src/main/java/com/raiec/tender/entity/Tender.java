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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A tender submission parsed from an IREPS PDF. Root of the
 * Tender -> Schedule -> ScheduleEntry -> BreakupItem tree.
 */
@Entity
@Table(name = "tender", indexes = {
        @Index(name = "idx_tender_no", columnList = "tender_no", unique = true),
        @Index(name = "idx_tender_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tender {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tender_no", nullable = false, unique = true, length = 64)
    private String tenderNo;

    @Column(name = "name_of_work", columnDefinition = "text")
    private String nameOfWork;

    /** Division, e.g. "Bikaner". */
    @Column(name = "division", length = 64)
    private String division;

    /** Originating post/office, e.g. "ADEN/HQ/Bikaner". */
    @Column(name = "post", length = 128)
    private String post;

    @Column(name = "tendering_section", length = 64)
    private String tenderingSection;

    @Column(name = "advertised_value", precision = 16, scale = 2)
    private BigDecimal advertisedValue;

    @Column(name = "earnest_money", precision = 14, scale = 2)
    private BigDecimal earnestMoney;

    @Column(name = "tender_doc_cost", precision = 12, scale = 2)
    private BigDecimal tenderDocCost;

    @Column(name = "contract_type", length = 64)
    private String contractType;

    @Column(name = "contract_category", length = 64)
    private String contractCategory;

    @Column(name = "expenditure_type", length = 64)
    private String expenditureType;

    @Column(name = "bidding_type", length = 64)
    private String biddingType;

    @Column(name = "tender_type", length = 64)
    private String tenderType;

    @Column(name = "bidding_system", length = 64)
    private String biddingSystem;

    @Column(name = "bidding_style", length = 64)
    private String biddingStyle;

    @Column(name = "period_of_completion", length = 64)
    private String periodOfCompletion;

    @Column(name = "validity_of_offer_days")
    private Integer validityOfOfferDays;

    @Column(name = "ranking_order", length = 64)
    private String rankingOrder;

    @Column(name = "uploading_date_time")
    private LocalDateTime uploadingDateTime;

    @Column(name = "closing_date_time")
    private LocalDateTime closingDateTime;

    @Column(name = "bidding_start_date")
    private LocalDate biddingStartDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TenderStatus status;

    /** Name of the uploaded PDF (provenance). */
    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @OneToMany(mappedBy = "tender", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Schedule> schedules = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Keeps both sides of the relationship in sync. */
    public void addSchedule(Schedule schedule) {
        schedules.add(schedule);
        schedule.setTender(this);
    }
}
