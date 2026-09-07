package com.raiec.settings.web;

import com.raiec.settings.entity.RateThresholds;
import com.raiec.settings.service.ThresholdService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;

/**
 * The tolerance bands used by the rate-match engine. Any signed-in user may read them —
 * the officer needs to see what the verdicts were measured against — but only an ADMIN may
 * change them (enforced in SecurityConfig).
 */
@RestController
@RequestMapping("/api/settings/thresholds")
public class ThresholdController {

    private final ThresholdService thresholdService;

    public ThresholdController(ThresholdService thresholdService) {
        this.thresholdService = thresholdService;
    }

    public record ThresholdResponse(BigDecimal warnPct, BigDecimal failPct,
                                    Integer larValidityMonths, BigDecimal fuzzyThreshold,
                                    String updatedBy, String updatedAt) {
        static ThresholdResponse from(RateThresholds t) {
            return new ThresholdResponse(t.getWarnPct(), t.getFailPct(), t.getLarValidityMonths(),
                    t.getFuzzyThreshold(), t.getUpdatedBy(),
                    t.getUpdatedAt() == null ? null : t.getUpdatedAt().toString());
        }
    }

    /** Partial update: omit a field to leave it unchanged. */
    public record ThresholdUpdateRequest(BigDecimal warnPct, BigDecimal failPct,
                                         Integer larValidityMonths, BigDecimal fuzzyThreshold) {
    }

    @GetMapping
    public ThresholdResponse get() {
        return ThresholdResponse.from(thresholdService.current());
    }

    @PutMapping
    public ThresholdResponse update(@RequestBody ThresholdUpdateRequest req, Principal principal) {
        return ThresholdResponse.from(thresholdService.update(
                req.warnPct(), req.failPct(), req.larValidityMonths(), req.fuzzyThreshold(),
                principal == null ? null : principal.getName()));
    }
}
