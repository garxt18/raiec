package com.raiec.tender.service;

import com.raiec.ai.service.AiAnalysisService;
import com.raiec.ai.web.dto.AiAnalysisResponse;
import com.raiec.ratematch.service.RateMatchService;
import com.raiec.ratematch.web.dto.RateMatchResponse;
import com.raiec.tender.web.dto.ApprovalResponse;
import com.raiec.tender.web.dto.BulkIngestResponse;
import com.raiec.tender.web.dto.BulkIngestResult;
import com.raiec.tender.web.dto.TenderSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Dev/seed utility: ingests every PDF in a folder through the full pipeline
 * (upload -> rate-match -> AI -> optional approve) so the dashboard and LAR dataset
 * can be populated from a batch of real tenders in one shot. Each file is independent;
 * a failure on one is recorded and the rest continue.
 */
@Service
public class BulkIngestService {

    private static final Logger log = LoggerFactory.getLogger(BulkIngestService.class);

    private final TenderService tenderService;
    private final RateMatchService rateMatchService;
    private final AiAnalysisService aiAnalysisService;

    public BulkIngestService(TenderService tenderService,
                             RateMatchService rateMatchService,
                             AiAnalysisService aiAnalysisService) {
        this.tenderService = tenderService;
        this.rateMatchService = rateMatchService;
        this.aiAnalysisService = aiAnalysisService;
    }

    public BulkIngestResponse ingestDirectory(String dir, boolean approve) {
        List<BulkIngestResult> results = new ArrayList<>();
        File folder = new File(dir);
        File[] files = folder.listFiles((d, n) -> n.toLowerCase().endsWith(".pdf"));
        if (files == null) {
            results.add(new BulkIngestResult(dir, "error", null, 0, 0, 0, 0, 0, null, 0, 0,
                    "Folder not found: " + folder.getAbsolutePath()));
            return new BulkIngestResponse(0, 0, 0, 1, 0, results);
        }
        Arrays.sort(files, Comparator.comparing(File::getName));

        int ingested = 0, duplicates = 0, errors = 0, larAdded = 0;
        for (File f : files) {
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                TenderSummaryResponse sum;
                try {
                    sum = tenderService.ingestPdf(bytes, f.getName());
                } catch (DuplicateTenderException de) {
                    duplicates++;
                    results.add(new BulkIngestResult(f.getName(), "duplicate", null, 0, 0, 0, 0, 0, null, 0, 0,
                            de.getMessage()));
                    continue;
                } catch (TenderParseException pe) {
                    errors++;
                    results.add(new BulkIngestResult(f.getName(), "parse-error", null, 0, 0, 0, 0, 0, null, 0, 0,
                            pe.getMessage()));
                    continue;
                }

                Long id = sum.id();
                RateMatchResponse rm = rateMatchService.evaluate(id);
                AiAnalysisResponse ai = aiAnalysisService.analyze(id);

                int la = 0, lu = 0;
                if (approve) {
                    ApprovalResponse ap = tenderService.approve(id);
                    la = ap.larAdded();
                    lu = ap.larUpdated();
                    larAdded += la;
                }
                ingested++;
                results.add(new BulkIngestResult(f.getName(), "ok", sum.tenderNo(),
                        rm.totalItems(), rm.matched(), rm.warn(), rm.fail(), rm.noReference(),
                        ai.status(), la, lu, null));
            } catch (Exception e) {
                errors++;
                log.warn("Bulk ingest failed for {}", f.getName(), e);
                results.add(new BulkIngestResult(f.getName(), "error", null, 0, 0, 0, 0, 0, null, 0, 0,
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return new BulkIngestResponse(files.length, ingested, duplicates, errors, larAdded, results);
    }
}
