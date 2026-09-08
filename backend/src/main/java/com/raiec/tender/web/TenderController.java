package com.raiec.tender.web;

import com.raiec.tender.service.TenderEventService;
import com.raiec.tender.service.TenderService;
import com.raiec.tender.web.dto.ApprovalResponse;
import com.raiec.tender.web.dto.TenderDetailResponse;
import com.raiec.tender.web.dto.TenderEventResponse;
import com.raiec.tender.web.dto.TenderStatsResponse;
import com.raiec.tender.web.dto.TenderSummaryResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * REST API for tenders.
 *
 * NOTE: these endpoints are currently UNAUTHENTICATED (Spring Security is deferred to the
 * auth phase). Suitable for local development only; must be secured before any deployment.
 */
@RestController
@RequestMapping("/api/tenders")
public class TenderController {

    private final TenderService tenderService;
    private final TenderEventService events;

    public TenderController(TenderService tenderService, TenderEventService events) {
        this.tenderService = tenderService;
        this.events = events;
    }

    /** The audit trail for one tender, oldest first. */
    @GetMapping("/{id}/events")
    public List<TenderEventResponse> events(@PathVariable Long id) {
        return events.forTender(id);
    }

    /** The most recent activity across every tender, for the dashboard feed. */
    @GetMapping("/events/recent")
    public List<TenderEventResponse> recentEvents() {
        return events.recent();
    }

    /** Upload a tender PDF; it is parsed and stored. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TenderSummaryResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        TenderSummaryResponse response = tenderService.ingestPdf(file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<TenderSummaryResponse> list() {
        return tenderService.listAll();
    }

    @GetMapping("/stats")
    public TenderStatsResponse stats() {
        return tenderService.stats();
    }

    @GetMapping("/{id}")
    public TenderDetailResponse get(@PathVariable Long id) {
        return tenderService.getDetail(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tenderService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/approve")
    public ApprovalResponse approve(@PathVariable Long id) {
        return tenderService.approve(id);
    }

    @PostMapping("/{id}/send-to-review")
    public ApprovalResponse sendToReview(@PathVariable Long id) {
        return tenderService.sendToReview(id);
    }

    @PostMapping("/{id}/reject")
    public ApprovalResponse reject(@PathVariable Long id) {
        return tenderService.reject(id);
    }

    /**
     * Officer needs clarification before deciding. This is a hold, not a verdict: the
     * tender stays open and can still be approved or rejected afterwards.
     */
    @PostMapping("/{id}/request-info")
    public ApprovalResponse requestInfo(@PathVariable Long id,
                                        @RequestBody(required = false) Map<String, String> body) {
        return tenderService.requestInfo(id, body == null ? null : body.get("remark"));
    }
}
