package com.raiec.reference.entity;

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

/**
 * A line item from the IRUSSOR (Indian Railways Unified Standard Schedule of Rates) rate book.
 * Source: IRUSSOR PDF (item code / description / unit / rate). Versioned by {@code edition}.
 */
@Entity
@Table(name = "irussor_item", indexes = {
        @Index(name = "idx_irussor_item_code", columnList = "item_code"),
        @Index(name = "idx_irussor_edition", columnList = "edition")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IrussorItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Rate-book item code, e.g. a chapter-21 code. */
    @Column(name = "item_code", nullable = false, length = 64)
    private String itemCode;

    /** Chapter the item belongs to, e.g. "21 - Miscellaneous". */
    @Column(name = "chapter", length = 128)
    private String chapter;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    /** Unit of measure, e.g. Cum, Sqm, MT, Each, m. */
    @Column(name = "unit", length = 32)
    private String unit;

    /** Published rate in INR. Stored as exact decimal (never floating point). */
    @Column(name = "rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal rate;

    /** Rate-book edition/year, e.g. "2021". Lets a tender match the correct version. */
    @Column(name = "edition", length = 16)
    private String edition;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
