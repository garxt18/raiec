package com.raiec.tender.entity;

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

import java.time.Instant;

/**
 * One thing that happened to a tender, and who caused it.
 *
 * <p>The vetting process is meant to be answerable after the fact: an estimate was
 * approved with items above their reference rates, and the question later is who
 * approved it, when, and what the system was telling them at that moment. Reconstructing
 * that from the tender's current status is impossible — status only records where a
 * tender ended up, not the path it took or the hands it passed through.
 *
 * <p>Events are append-only. Nothing in the application updates or deletes a row here
 * except the cascade when its tender is removed; a trail that can be edited answers
 * no question worth asking.
 */
@Entity
@Table(name = "tender_event", indexes = {
        @Index(name = "idx_event_tender", columnList = "tender_id"),
        @Index(name = "idx_event_at", columnList = "at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Deliberately a plain column rather than a @ManyToOne. The trail is written on
     * almost every request and read as a flat list; an association would buy nothing
     * and cost a load on every write.
     */
    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    /** Human-readable line, e.g. "Rate match completed — 4 items above tolerance". */
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    /**
     * Username of whoever caused this, or "system" for work no person initiated.
     * Stored as text rather than a user reference so the trail survives the account
     * being renamed or removed — the record of who acted must outlive the actor.
     */
    @Column(name = "actor", length = 128)
    private String actor;

    /**
     * Rupees above reference at the moment of the event, where the event had one.
     * Kept on the row rather than recomputed later: rate books and thresholds change,
     * and the question is what the officer was shown when they decided, not what the
     * same tender would score today.
     */
    @Column(name = "excess_at_event", precision = 16, scale = 2)
    private java.math.BigDecimal excessAtEvent;

    @CreationTimestamp
    @Column(name = "at", updatable = false)
    private Instant at;
}
