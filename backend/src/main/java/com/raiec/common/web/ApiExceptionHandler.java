package com.raiec.common.web;

import com.raiec.tender.service.DuplicateTenderException;
import com.raiec.tender.service.TenderNotFoundException;
import com.raiec.tender.service.TenderParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/** Translates application exceptions into consistent JSON error responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(Instant timestamp, int status, String error, String message) {
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message));
    }

    /**
     * A duplicate returns the existing record alongside the message, so the client can
     * show the officer which submission this collides with instead of a bare "already
     * exists" that leaves them guessing.
     */
    @ExceptionHandler(DuplicateTenderException.class)
    public ResponseEntity<DuplicateError> handleDuplicate(DuplicateTenderException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new DuplicateError(
                Instant.now().toString(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                e.getMessage(),
                new DuplicateDetail(
                        e.getExistingId(),
                        e.getTenderNo(),
                        e.getNameOfWork(),
                        e.getStatus(),
                        e.getUploadedAt() == null ? null : e.getUploadedAt().toString(),
                        e.getOriginalFileName())));
    }

    public record DuplicateDetail(Long id, String tenderNo, String nameOfWork,
                                  String status, String uploadedAt, String originalFileName) {
    }

    public record DuplicateError(String timestamp, int status, String error,
                                 String message, DuplicateDetail existing) {
    }

    @ExceptionHandler(TenderNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(TenderNotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({TenderParseException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
