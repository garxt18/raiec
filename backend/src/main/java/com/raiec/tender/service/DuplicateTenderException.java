package com.raiec.tender.service;

import java.time.Instant;

/**
 * Thrown when a tender with the same tender number already exists.
 *
 * <p>Carries the existing record's identity as well as the message. Being told only
 * "already exists" leaves the officer with no way to tell whether this is the same
 * document they uploaded a minute ago or someone else's submission from last month,
 * so the details travel with the error.
 */
public class DuplicateTenderException extends RuntimeException {

    private final String tenderNo;
    private final Long existingId;
    private final String nameOfWork;
    private final String status;
    private final Instant uploadedAt;
    private final String originalFileName;

    public DuplicateTenderException(String tenderNo, Long existingId, String nameOfWork,
                                    String status, Instant uploadedAt, String originalFileName) {
        super("A tender with number '" + tenderNo + "' has already been scanned.");
        this.tenderNo = tenderNo;
        this.existingId = existingId;
        this.nameOfWork = nameOfWork;
        this.status = status;
        this.uploadedAt = uploadedAt;
        this.originalFileName = originalFileName;
    }

    public String getTenderNo() { return tenderNo; }
    public Long getExistingId() { return existingId; }
    public String getNameOfWork() { return nameOfWork; }
    public String getStatus() { return status; }
    public Instant getUploadedAt() { return uploadedAt; }
    public String getOriginalFileName() { return originalFileName; }
}
