package com.raiec.ai.web;

import com.raiec.ai.service.AiAnalysisService;
import com.raiec.ai.service.AiAssessmentService;
import com.raiec.ai.web.dto.AiAnalysisResponse;
import com.raiec.ai.web.dto.AiAssessmentResponse;
import com.raiec.tender.service.TenderEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenders")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final AiAssessmentService aiAssessmentService;
    private final TenderEventService events;

    public AiAnalysisController(AiAnalysisService aiAnalysisService,
                                AiAssessmentService aiAssessmentService,
                                TenderEventService events) {
        this.aiAnalysisService = aiAnalysisService;
        this.aiAssessmentService = aiAssessmentService;
        this.events = events;
    }

    @GetMapping("/{id}/ai-analysis")
    public AiAnalysisResponse analyze(@PathVariable Long id) {
        AiAnalysisResponse result = aiAnalysisService.analyze(id);
        events.recordOnce(id, TenderEventService.AI_ANALYSED,
                "AI analysis completed — verdict " + result.status() + ", "
                        + result.fail() + " failed check(s), " + result.warn() + " warning(s)",
                null);
        return result;
    }

    /** Plain-language risk assessment (LLM when configured, rule-based fallback otherwise). */
    @GetMapping("/{id}/ai-summary")
    public AiAssessmentResponse summary(@PathVariable Long id) {
        return aiAssessmentService.assess(id);
    }
}
