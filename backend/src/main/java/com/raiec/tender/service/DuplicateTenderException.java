package com.raiec.tender.service;

/** Thrown when a tender with the same tender number already exists. */
public class DuplicateTenderException extends RuntimeException {
    public DuplicateTenderException(String tenderNo) {
        super("A tender with number '" + tenderNo + "' already exists.");
    }
}
