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

    @ExceptionHandler(DuplicateTenderException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateTenderException e) {
        return build(HttpStatus.CONFLICT, e.getMessage());
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
