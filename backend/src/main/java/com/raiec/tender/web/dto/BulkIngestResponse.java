package com.raiec.tender.web.dto;

import java.util.List;

public record BulkIngestResponse(
        int totalFiles,
        int ingested,
        int duplicates,
        int errors,
        int totalLarAdded,
        List<BulkIngestResult> files
) {
}
