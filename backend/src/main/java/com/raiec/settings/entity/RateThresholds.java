package com.raiec.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The tolerance bands a vetting officer works to, held as data rather than compiled
 * constants so they can be adjusted without a rebuild.
 *
 * <p>Single row, fixed id {@link #SINGLETON_ID}: these are department-wide settings, not
 * per-tender. Kept in the database rather than a properties file so a change is auditable
 * and takes effect for everyone at once.
 */
@Entity
@Table(name = "rate_thresholds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateThresholds {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Quoting above the reference by more than this, but within {@link #failPct}, is a WARN. */
    @Column(name = "warn_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal warnPct;

    /** Quoting above the reference by more than this is a FAIL. */
    @Column(name = "fail_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal failPct;

    /**
     * How long a Last Accepted Rate stays current. The vetting rule is "latest AND least":
     * the benchmark is the lowest rate among recent ones. Older rates are still used, but
     * flagged, since a stale benchmark beats no benchmark.
     */
    @Column(name = "lar_validity_months", nullable = false)
    private Integer larValidityMonths;

    /** Minimum trigram similarity (0-1) for a fuzzy description match to be trusted. */
    @Column(name = "fuzzy_threshold", nullable = false, precision = 4, scale = 3)
    private BigDecimal fuzzyThreshold;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /** The values RAIEC shipped with, used when no row exists yet. */
    public static RateThresholds defaults() {
        return RateThresholds.builder()
                .id(SINGLETON_ID)
                .warnPct(new BigDecimal("5.00"))
                .failPct(new BigDecimal("10.00"))
                .larValidityMonths(12)
                .fuzzyThreshold(new BigDecimal("0.450"))
                .build();
    }
}
