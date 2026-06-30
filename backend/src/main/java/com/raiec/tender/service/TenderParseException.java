package com.raiec.tender.service;

/** Thrown when a tender PDF cannot be read or parsed. */
public class TenderParseException extends RuntimeException {
    public TenderParseException(String message) {
        super(message);
    }

    public TenderParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
