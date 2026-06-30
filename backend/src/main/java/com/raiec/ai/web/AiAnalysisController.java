package com.raiec.ai.web;

import com.raiec.ai.service.AiAnalysisService;
import com.raiec.ai.service.AiAssessmentService;
import com.raiec.ai.web.dto.AiAnalysisResponse;
import com.raiec.ai.web.dto.AiAssessmentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenders")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final AiAssessmentService aiAssessmentService;

    public AiAnalysisController(AiAnalysisService aiAnalysisService,
                                AiAssessmentService aiAssessmentService) {
        this.aiAnalysisService = aiAnalysisService;
        this.aiAssessmentService = aiAssessmentService;
    }

    @GetMapping("/{id}/ai-analysis")
    public AiAnalysisResponse analyze(@PathVariable Long id) {
        return aiAnalysisService.analyze(id);
    }

    /** Plain-language risk assessment (LLM when configured, rule-based fallback otherwise). */
    @GetMapping("/{id}/ai-summary")
    public AiAssessmentResponse summary(@PathVariable Long id) {
        return aiAssessmentService.assess(id);
    }
}
