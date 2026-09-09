package com.raiec.search.web;

import com.raiec.search.service.SearchService;
import com.raiec.search.web.dto.SearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Search tenders by number, title, office or the work described in their line items.
     *
     * @param q     what the officer typed
     * @param limit how many results to return; capped so a stray value cannot ask the
     *              server to serialise the whole archive
     */
    @GetMapping
    public SearchResponse search(@RequestParam(name = "q", defaultValue = "") String q,
                                 @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return searchService.search(q, Math.clamp(limit, 1, 50));
    }
}
