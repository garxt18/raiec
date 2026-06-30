package com.raiec.lar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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

/**
 * A Last Accepted Rate (LAR) record, built from a Non-Scheduled (NS) item of an approved tender.
 * There is no published rate book for NS items, so this dataset is grown from approvals.
 * Matched against future tender NS items by description similarity (pg_trgm, added later).
 */
@Entity
@Table(name = "lar_record", indexes = {
        @Index(name = "idx_lar_source_tender", columnList = "source_tender_no")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LarRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Generated identifier, sequential per year, e.g. "LAR-2026-00001". */
    @Column(name = "lar_code", nullable = false, unique = true, length = 32)
    private String larCode;

    /** Full NS item description - the basis for fuzzy matching future tenders. */
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** Unit of measure, e.g. Cum, Sqm, MT, Each, m. */
    @Column(name = "unit", length = 32)
    private String unit;

    /** The accepted unit rate in INR (the "last accepted rate"). */
    @Column(name = "rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal rate;

    /** Quantity from the source tender (optional context). */
    @Column(name = "quantity", precision = 14, scale = 3)
    private BigDecimal quantity;

    /** Amount from the source tender (optional context). */
    @Column(name = "amount", precision = 16, scale = 2)
    private BigDecimal amount;

    /** Escalation percentage; NS items are usually "AT Par" (0.00). */
    @Column(name = "escalation_pct", precision = 6, scale = 2)
    private BigDecimal escalationPct;

    /** Tender number this rate was accepted in (provenance). */
    @Column(name = "source_tender_no", length = 64)
    private String sourceTenderNo;

    /** Division, e.g. "Bikaner". */
    @Column(name = "division", length = 64)
    private String division;

    /** Originating post/office, e.g. "ADEN/HQ/Bikaner". */
    @Column(name = "source_post", length = 128)
    private String sourcePost;

    /** Date this rate entered the LAR dataset (on approval). */
    @Column(name = "approved_on")
    private LocalDate approvedOn;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
