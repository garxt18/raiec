package com.raiec.reference.web;

import com.raiec.reference.service.ReferenceImportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoint to load rate-book data. Restricted to ADMIN by
 * {@link com.raiec.common.web.SecurityConfig}.
 *
 * <p>The edition defaults match the shipped rate-book PDFs and, more importantly, the edition
 * a tender cites ("CPWD DSR 2021 Items"). The edition is the officer's audit trail for which
 * published rate justified a decision, so it must name the book the rate actually came from.
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceImportController {

    private final ReferenceImportService referenceImportService;

    public ReferenceImportController(ReferenceImportService referenceImportService) {
        this.referenceImportService = referenceImportService;
    }

    @PostMapping("/import/irussor")
    public Map<String, Object> importIrussor(
            @RequestParam(defaultValue = "irussor-2021.pdf") String file,
            @RequestParam(defaultValue = "2021") String edition) {
        int n = referenceImportService.importIrussor(file, edition);
        return Map.of("imported", n, "edition", edition, "file", file);
    }

    @PostMapping("/import/dsr")
    public Map<String, Object> importDsr(@RequestParam(defaultValue = "2021") String edition) {
        int n = referenceImportService.importDsr(edition);
        return Map.of("imported", n, "edition", edition);
    }
}
