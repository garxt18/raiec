package com.raiec.lar.web;

import com.raiec.lar.service.LarService;
import com.raiec.lar.web.dto.LarRecordResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The Last Accepted Rates dataset. Readable by any signed-in user; adding a rate by hand
 * is ADMIN-only (enforced in SecurityConfig), because a hand-entered benchmark carries the
 * same weight in future vetting as one earned through an approval.
 */
@RestController
@RequestMapping("/api/lar")
public class LarController {

    private final LarService larService;

    public LarController(LarService larService) {
        this.larService = larService;
    }

    /** Fields the officer supplies; the LAR code and defaults are assigned server-side. */
    public record NewLarRequest(String description, String unit, BigDecimal rate,
                                BigDecimal quantity, String division, String sourcePost,
                                String sourceTenderNo, LocalDate approvedOn) {
    }

    @GetMapping
    public List<LarRecordResponse> list() {
        return larService.listAll();
    }

    @PostMapping
    public ResponseEntity<LarRecordResponse> add(@RequestBody NewLarRequest req) {
        LarRecordResponse saved = larService.addManual(
                req.description(), req.unit(), req.rate(), req.quantity(),
                req.division(), req.sourcePost(), req.sourceTenderNo(), req.approvedOn());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
