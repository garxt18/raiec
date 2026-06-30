package com.raiec.lar.web;

import com.raiec.lar.service.LarService;
import com.raiec.lar.web.dto.LarRecordResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read API for the Last Accepted Rates dataset.
 * NOTE: unauthenticated for now (security deferred to the auth phase).
 */
@RestController
@RequestMapping("/api/lar")
public class LarController {

    private final LarService larService;

    public LarController(LarService larService) {
        this.larService = larService;
    }

    @GetMapping
    public List<LarRecordResponse> list() {
        return larService.listAll();
    }
}
