package com.raiec.ai.service;

import com.raiec.ai.llm.LlmClient;
import com.raiec.ai.web.dto.AiAnalysisResponse;
import com.raiec.ai.web.dto.AiAssessmentResponse;
import com.raiec.ai.web.dto.AiCheck;
import com.raiec.ratematch.service.RateMatchService;
import com.raiec.ratematch.web.dto.RateMatchResponse;
import com.raiec.tender.entity.Tender;
import com.raiec.tender.repository.TenderRepository;
import com.raiec.tender.service.TenderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Produces a plain-language risk assessment for the reviewing officer. It builds context from
 * the rate-match and rule-based AI checks, then asks the configured LLM to narrate it. If no
 * LLM is configured (or the call fails), it returns a synthesised rule-based narrative instead,
 * so the feature always works.
 */
@Service
public class AiAssessmentService {

    private static final String SYSTEM_PROMPT =
            "You are RAIEC, an estimate-vetting assistant for North Western Railway's Civil & Construction unit. "
            + "You are given the automated analysis of a construction tender estimate. Write a concise professional "
            + "assessment (about 120-160 words, plain text, no markdown headers or bullet characters) for the reviewing "
            + "officer: state the overall risk, the most material findings with specific numbers, and end with a clear "
            + "recommendation (approve / approve with conditions / seek clarification / reject). Lead with the rupee "
            + "impact rather than item counts, since that is what determines whether a finding is worth acting on, and "
            + "say so plainly if only part of the estimate could be checked. Be factual and do not invent any data "
            + "beyond what is provided.";

    private final TenderRepository tenderRepository;
    private final RateMatchService rateMatchService;
    private final AiAnalysisService aiAnalysisService;
    private final LlmClient llmClient;

    public AiAssessmentService(TenderRepository tenderRepository,
                               RateMatchService rateMatchService,
                               AiAnalysisService aiAnalysisService,
                               LlmClient llmClient) {
        this.tenderRepository = tenderRepository;
        this.rateMatchService = rateMatchService;
        this.aiAnalysisService = aiAnalysisService;
        this.llmClient = llmClient;
    }

    @Transactional(readOnly = true)
    public AiAssessmentResponse assess(Long tenderId) {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new TenderNotFoundException(tenderId));
        RateMatchResponse rm = rateMatchService.evaluate(tenderId);
        AiAnalysisResponse ai = aiAnalysisService.analyze(tenderId);
        String risk = AiAssessmentNarrator.deriveRisk(ai, rm);

        if (llmClient.isEnabled()) {
            try {
                String text = llmClient.complete(SYSTEM_PROMPT, buildContext(tender, rm, ai, risk));
                if (text != null && !text.isBlank()) {
                    return new AiAssessmentResponse(tender.getTenderNo(), "llm", risk, text.trim(),
                            Instant.now().toString());
                }
            } catch (RuntimeException ignored) {
                // any LLM failure -> fall back to the rule-based narrative below
            }
        }

        String summary = AiAssessmentNarrator.fallbackSummary(
                tender.getTenderNo(), tender.getNameOfWork(), rm, ai, risk);
        return new AiAssessmentResponse(tender.getTenderNo(), "rule-based", risk, summary,
                Instant.now().toString());
    }

    private String buildContext(Tender t, RateMatchResponse rm, AiAnalysisResponse ai, String risk) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tender: ").append(t.getTenderNo()).append("\n");
        sb.append("Work: ").append(t.getNameOfWork()).append("\n");
        if (t.getAdvertisedValue() != null) {
            sb.append("Advertised value (INR): ").append(t.getAdvertisedValue()).append("\n");
        }
        sb.append("Computed risk: ").append(risk).append("\n");
        // Give the model the money, not just the counts: "three items over tolerance"
        // and "three items over tolerance worth 4 lakh" call for different decisions.
        if (rm.financials() != null) {
            var f = rm.financials();
            sb.append("Estimate value (INR): ").append(f.quotedValue()).append("\n");
            sb.append("Quoted ABOVE reference (INR): ").append(f.excessTotal()).append("\n");
            sb.append("Quoted BELOW reference (INR): ").append(f.savingTotal()).append("\n");
            sb.append("Share of value that could be checked: ").append(f.coveragePct()).append("%\n");
            if (f.coveragePct().compareTo(new java.math.BigDecimal("60")) < 0) {
                sb.append("NOTE: most of this estimate has no reference rate, so the figures above cover only part of it.\n");
            }
        }
        sb.append("Rate match — total ").append(rm.totalItems())
                .append(", within tolerance ").append(rm.matched())
                .append(", 5-10% over ").append(rm.warn())
                .append(", >10% over ").append(rm.fail())
                .append(", no reference ").append(rm.noReference()).append("\n");
        // Items arrive sorted by excess, so this is the eight costliest rather than the
        // eight that happened to appear first in the PDF.
        sb.append("Worst items by rupee impact (description | source | tenderRate | refRate | variance% | excess INR):\n");
        if (rm.items() != null) {
            rm.items().stream()
                    .filter(it -> "FAIL".equals(it.status()) || "WARN".equals(it.status()))
                    .limit(8)
                    .forEach(it -> sb.append("- ").append(it.description())
                            .append(" | ").append(it.source())
                            .append(" | ").append(it.tenderRate())
                            .append(" | ").append(it.referenceRate())
                            .append(" | ").append(it.variancePct())
                            .append(" | ").append(it.excessAmount()).append("\n"));
        }
        sb.append("Automated checks:\n");
        for (AiCheck c : ai.checks()) {
            sb.append("- ").append(c.code()).append(" ").append(c.title())
                    .append(": ").append(c.status()).append(" — ").append(c.detail()).append("\n");
        }
        return sb.toString();
    }
}
