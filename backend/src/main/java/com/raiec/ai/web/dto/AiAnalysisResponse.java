package com.raiec.ai.web.dto;

import java.util.List;

public record AiAnalysisResponse(
        String tenderNo,
        String status,
        int totalChecks,
        int pass,
        int warn,
        int fail,
        List<AiCheck> checks
) {
}
