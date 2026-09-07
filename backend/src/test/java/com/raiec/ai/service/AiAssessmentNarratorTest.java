package com.raiec.ai.service;

import com.raiec.ai.web.dto.AiAnalysisResponse;
import com.raiec.ai.web.dto.AiCheck;
import com.raiec.ratematch.web.dto.RateMatchItem;
import com.raiec.ratematch.web.dto.RateMatchResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiAssessmentNarratorTest {

    private RateMatchItem item(String desc, String status, String variance) {
        return new RateMatchItem("A", "C-1", desc, "DSR",
                new BigDecimal("10"), "cum", new BigDecimal("100"), new BigDecimal("1000"),
                new BigDecimal("87"), "DSR 2023", BigDecimal.ZERO, true, false,
                variance == null ? null : new BigDecimal(variance), status);
    }

    private RateMatchResponse rm(int total, int matched, int warn, int fail, int noRef, List<RateMatchItem> items) {
        return new RateMatchResponse("232-25-26", "OCR_EXTRACTED", total, matched, warn, fail, noRef, items);
    }

    private AiAnalysisResponse ai(int pass, int warn, int fail, List<AiCheck> checks) {
        return new AiAnalysisResponse("232-25-26", fail > 0 ? "FAIL" : (warn > 0 ? "WARN" : "PASS"),
                checks.size(), pass, warn, fail, checks);
    }

    @Test
    void riskIsHighWhenAnythingFails() {
        var rmResp = rm(5, 3, 1, 1, 0, List.of(item("Excavation in rock", "FAIL", "15.00")));
        var aiResp = ai(3, 0, 1, List.of(new AiCheck("PS-01", "Manual rate checking", "FAIL", "1 item over 10%.")));
        assertEquals("HIGH", AiAssessmentNarrator.deriveRisk(aiResp, rmResp));
    }

    @Test
    void riskIsMediumWhenOnlyWarnings() {
        var rmResp = rm(5, 4, 1, 0, 0, List.of(item("Brick masonry", "WARN", "7.50")));
        var aiResp = ai(4, 0, 0, List.of(new AiCheck("PS-01", "Manual rate checking", "PASS", "ok")));
        assertEquals("MEDIUM", AiAssessmentNarrator.deriveRisk(aiResp, rmResp));
    }

    @Test
    void riskIsLowWhenAllClear() {
        var rmResp = rm(5, 5, 0, 0, 0, List.of(item("Cement concrete", "OK", "-3.00")));
        var aiResp = ai(4, 0, 0, List.of(new AiCheck("PS-01", "Manual rate checking", "PASS", "ok")));
        assertEquals("LOW", AiAssessmentNarrator.deriveRisk(aiResp, rmResp));
    }

    @Test
    void fallbackNarrativeMentionsKeyFactsAndRecommendation() {
        var rmResp = rm(5, 3, 1, 1, 0, List.of(item("Excavation in rock", "FAIL", "15.00")));
        var aiResp = ai(3, 0, 1, List.of(new AiCheck("PS-01", "Manual rate checking", "FAIL", "1 item over 10%.")));
        String text = AiAssessmentNarrator.fallbackSummary("232-25-26", "Civil works at Bikaner", rmResp, aiResp, "HIGH");

        assertFalse(text.isBlank());
        assertTrue(text.contains("232-25-26"), "should name the tender");
        assertTrue(text.contains("HIGH"), "should state the risk");
        assertTrue(text.contains("+15.00%"), "should cite the over-quoted variance");
        assertTrue(text.toLowerCase().contains("recommendation"), "should end with a recommendation");
    }
}
