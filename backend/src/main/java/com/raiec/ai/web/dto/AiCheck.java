package com.raiec.ai.web.dto;

/** One problem-statement check result. status: PASS | WARN | FAIL. */
public record AiCheck(
        String code,
        String title,
        String status,
        String detail
) {
}
