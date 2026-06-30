package com.raiec.tender.web.dto;

/**
 * Outcome of bulk-ingesting one PDF.
 * @param result ok | duplicate | parse-error | error
 */
public record BulkIngestResult(
        String file,
        String result,
        String tenderNo,
        int items,
        int matched,
        int warn,
        int fail,
        int noReference,
        String aiStatus,
        int larAdded,
        int larUpdated,
        String message
) {
}
