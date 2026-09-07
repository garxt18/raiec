package com.raiec.ai.service;

import com.raiec.ai.web.dto.AiAnalysisResponse;
import com.raiec.ai.web.dto.AiCheck;
import com.raiec.ratematch.service.RateMatchService;
import com.raiec.ratematch.web.dto.RateMatchItem;
import com.raiec.ratematch.web.dto.RateMatchResponse;
import com.raiec.tender.entity.Tender;
import com.raiec.tender.repository.TenderRepository;
import com.raiec.tender.service.TenderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Rule-based "AI analysis" (step 4). Each problem-statement check is computed from data we
 * already have - no LLM cost. (An LLM explanation layer can be added in the final phase.)
 */
@Service
public class AiAnalysisService {

    private static final BigDecimal ONE_PERCENT = new BigDecimal("0.01");

    private final RateMatchService rateMatchService;
    private final TenderRepository tenderRepository;

    public AiAnalysisService(RateMatchService rateMatchService, TenderRepository tenderRepository) {
        this.rateMatchService = rateMatchService;
        this.tenderRepository = tenderRepository;
    }

    @Transactional(readOnly = true)
    public AiAnalysisResponse analyze(Long tenderId) {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new TenderNotFoundException(tenderId));
        RateMatchResponse rm = rateMatchService.evaluate(tenderId);

        List<AiCheck> checks = new ArrayList<>();
        checks.add(manualRateChecking(rm));
        checks.add(larLowestRate(rm));
        checks.add(mathVerification(rm));
        checks.add(duplicateDetection(tender));

        int pass = 0, warn = 0, fail = 0;
        for (AiCheck c : checks) {
            switch (c.status()) {
                case "PASS" -> pass++;
                case "WARN" -> warn++;
                default -> fail++;
            }
        }
        String overall = fail > 0 ? "FAIL" : (warn > 0 ? "WARN" : "PASS");
        return new AiAnalysisResponse(tender.getTenderNo(), overall, checks.size(), pass, warn, fail, checks);
    }

    /** PS-01: items quoted outside the rate tolerance vs their reference. */
    private AiCheck manualRateChecking(RateMatchResponse rm) {
        if (rm.fail() > 0) {
            String d = rm.fail() + " item(s) exceed the 10% tolerance vs reference rates"
                    + (rm.warn() > 0 ? ", " + rm.warn() + " more within 5-10%." : ".");
            return new AiCheck("PS-01", "Manual rate checking", "FAIL", d);
        }
        if (rm.warn() > 0) {
            return new AiCheck("PS-01", "Manual rate checking", "WARN",
                    rm.warn() + " item(s) are 5-10% off the reference rate.");
        }
        String d = rm.noReference() > 0
                ? "Matched rates are within tolerance (" + rm.noReference() + " items have no loaded reference yet)."
                : "All rates are within tolerance.";
        return new AiCheck("PS-01", "Manual rate checking", "PASS", d);
    }

    /** PS-02: NS items quoted above the lowest accepted LAR rate, and stale LAR references. */
    private AiCheck larLowestRate(RateMatchResponse rm) {
        int aboveLowest = 0;
        int nsMatched = 0;
        int stale = 0;
        for (RateMatchItem it : rm.items()) {
            if ("NS".equals(it.source()) && it.referenceRate() != null) {
                nsMatched++;
                if (it.referenceStale()) {
                    stale++;
                }
                if (it.tenderRate() != null && it.tenderRate().compareTo(it.referenceRate()) > 0) {
                    aboveLowest++;
                }
            }
        }
        String staleNote = stale > 0
                ? " " + stale + " reference(s) are older than 12 months and may be out of date."
                : "";
        if (aboveLowest > 0) {
            return new AiCheck("PS-02", "LAR recency & lowest-rate", "WARN",
                    aboveLowest + " NS item(s) quoted above the lowest accepted LAR rate." + staleNote);
        }
        if (nsMatched > 0) {
            return new AiCheck("PS-02", "LAR recency & lowest-rate", stale > 0 ? "WARN" : "PASS",
                    "All matched NS items are at or below the lowest accepted LAR rate." + staleNote);
        }
        return new AiCheck("PS-02", "LAR recency & lowest-rate", "PASS",
                "No NS items matched the LAR dataset yet.");
    }

    /** PS-03: quantity x rate should equal amount. */
    private AiCheck mathVerification(RateMatchResponse rm) {
        int checked = 0;
        int bad = 0;
        for (RateMatchItem it : rm.items()) {
            if (it.quantity() == null || it.tenderRate() == null || it.amount() == null) {
                continue;
            }
            checked++;
            BigDecimal expected = it.quantity().multiply(it.tenderRate());
            BigDecimal tolerance = it.amount().abs().multiply(ONE_PERCENT).max(BigDecimal.ONE);
            if (expected.subtract(it.amount()).abs().compareTo(tolerance) > 0) {
                bad++;
            }
        }
        if (bad > 0) {
            return new AiCheck("PS-03", "Math verification", "FAIL",
                    bad + " of " + checked + " item(s) have quantity x rate not equal to amount.");
        }
        return new AiCheck("PS-03", "Math verification", "PASS",
                "All " + checked + " item(s) verified: quantity x rate = amount.");
    }

    /** PS-04: another tender with the same work (possible duplicate). */
    private AiCheck duplicateDetection(Tender tender) {
        String thisName = normalize(tender.getNameOfWork());
        if (!thisName.isEmpty()) {
            for (Tender other : tenderRepository.findAll()) {
                if (other.getId().equals(tender.getId())) {
                    continue;
                }
                if (thisName.equals(normalize(other.getNameOfWork()))) {
                    return new AiCheck("PS-04", "Duplicate proposal detection", "WARN",
                            "Possible duplicate: same work as tender " + other.getTenderNo() + ".");
                }
            }
        }
        return new AiCheck("PS-04", "Duplicate proposal detection", "PASS",
                "No overlapping tender found in recent submissions.");
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
