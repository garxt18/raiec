package com.raiec.tender.service;

/** Thrown when a tender cannot be found by id. */
public class TenderNotFoundException extends RuntimeException {
    public TenderNotFoundException(Long id) {
        super("Tender not found: " + id);
    }
}
