package com.raiec.ratematch.web;

import com.raiec.ratematch.service.RateMatchService;
import com.raiec.ratematch.web.dto.RateMatchResponse;
import com.raiec.tender.service.TenderEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenders")
public class RateMatchController {

    private final RateMatchService rateMatchService;
    private final TenderEventService events;

    public RateMatchController(RateMatchService rateMatchService, TenderEventService events) {
        this.rateMatchService = rateMatchService;
        this.events = events;
    }

    @GetMapping("/{id}/rate-match")
    public RateMatchResponse rateMatch(@PathVariable Long id) {
        RateMatchResponse result = rateMatchService.evaluate(id);

        // Logged here rather than in the service so that the trail records a result someone
        // was actually shown, not every internal evaluation the application happens to run.
        String detail = "Rate match completed — " + result.fail() + " item(s) above tolerance, "
                + result.warn() + " within warning band, " + result.noReference() + " with no reference";
        events.recordOnce(id, TenderEventService.RATE_MATCHED, detail,
                result.financials() != null ? result.financials().excessTotal() : null);

        return result;
    }
}
