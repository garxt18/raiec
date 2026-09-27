package com.raiec.tender.web;

import com.raiec.tender.service.BulkIngestService;
import com.raiec.tender.service.TenderEventService;
import com.raiec.tender.web.dto.AccountabilityResponse;
import com.raiec.tender.web.dto.BulkIngestResponse;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final TenderEventService events;

    public AdminController(BulkIngestService bulkIngestService, TenderEventService events) {
        this.bulkIngestService = bulkIngestService;
        this.events = events;
    }

    /**
     * Who has been deciding tenders, and what those decisions were worth. Admin-only: it
     * names individuals, so it is a supervisory view rather than a general one.
     */
    @GetMapping("/accountability")
    public AccountabilityResponse accountability() {
        return events.accountability();
    }

    @PostMapping("/bulk-ingest")
    public BulkIngestResponse bulkIngest(
            @RequestParam(name = "dir", defaultValue = "../sample-tenders/batch") String dir,
            @RequestParam(name = "approve", defaultValue = "true") boolean approve) {
        return bulkIngestService.ingestDirectory(dir, approve);
    }
}
