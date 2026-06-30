package com.raiec.ratematch.web;

import com.raiec.ratematch.service.RateMatchService;
import com.raiec.ratematch.web.dto.RateMatchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenders")
public class RateMatchController {

    private final RateMatchService rateMatchService;

    public RateMatchController(RateMatchService rateMatchService) {
        this.rateMatchService = rateMatchService;
    }

    @GetMapping("/{id}/rate-match")
    public RateMatchResponse rateMatch(@PathVariable Long id) {
        return rateMatchService.evaluate(id);
    }
}
