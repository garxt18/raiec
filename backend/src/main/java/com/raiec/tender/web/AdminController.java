package com.raiec.tender.web;

import com.raiec.tender.service.BulkIngestService;
import com.raiec.tender.web.dto.BulkIngestResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only utilities. The bulk-ingest endpoint seeds the system from a folder of PDFs.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final BulkIngestService bulkIngestService;

    public AdminController(BulkIngestService bulkIngestService) {
        this.bulkIngestService = bulkIngestService;
    }

    @PostMapping("/bulk-ingest")
    public BulkIngestResponse bulkIngest(
            @RequestParam(name = "dir", defaultValue = "../sample-tenders/batch") String dir,
            @RequestParam(name = "approve", defaultValue = "true") boolean approve) {
        return bulkIngestService.ingestDirectory(dir, approve);
    }
}
