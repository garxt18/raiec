package com.raiec.reference.service;

import com.raiec.reference.entity.IrussorItem;
import com.raiec.reference.entity.DsrItem;
import com.raiec.reference.ingest.IrussorImporter;
import com.raiec.reference.ingest.DsrImporter;
import com.raiec.reference.repository.IrussorItemRepository;
import com.raiec.reference.web.dto.ReferenceStatus;
import com.raiec.lar.repository.LarRecordRepository;
import com.raiec.reference.repository.DsrItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads rate-book PDFs (from the project's rate-books/ folder) into the reference tables.
 */
@Service
public class ReferenceImportService {

    private final IrussorImporter irussorImporter;
    private final IrussorItemRepository irussorItemRepository;
    private final DsrImporter dsrImporter;
    private final DsrItemRepository dsrItemRepository;

    private final LarRecordRepository larRecordRepository;

    public ReferenceImportService(IrussorImporter irussorImporter,
                                  IrussorItemRepository irussorItemRepository,
                                  DsrImporter dsrImporter,
                                  DsrItemRepository dsrItemRepository,
                                  LarRecordRepository larRecordRepository) {
        this.irussorImporter = irussorImporter;
        this.irussorItemRepository = irussorItemRepository;
        this.dsrImporter = dsrImporter;
        this.dsrItemRepository = dsrItemRepository;
        this.larRecordRepository = larRecordRepository;
    }

    /**
     * What the system currently has to check estimates against.
     *
     * <p>See {@link ReferenceStatus}: with no rate book loaded, every scheduled item is
     * unmatched and the result reads as a clean estimate. Callers use this to say the tool
     * is not ready instead of presenting an empty check as a pass.
     */
    @Transactional(readOnly = true)
    public ReferenceStatus status() {
        long dsr = dsrItemRepository.count();
        long irussor = irussorItemRepository.count();
        long lar = larRecordRepository.count();
        boolean ready = dsr > 0 || irussor > 0;

        String note;
        if (ready && lar > 0) {
            note = "Rate books loaded. Scheduled items are checked against IRUSSOR and CPWD DSR,"
                    + " and Non-Scheduled items against " + lar + " previously accepted rate(s).";
        } else if (ready) {
            note = "Rate books loaded. No Last Accepted Rates recorded yet, so Non-Scheduled items"
                    + " have nothing to be compared against until some tenders are approved.";
        } else {
            note = "No rate book is loaded, so nothing can be checked against a published rate."
                    + " Import IRUSSOR and CPWD DSR before relying on any result.";
        }
        return new ReferenceStatus(dsr, irussor, lar, ready, note);
    }

    @Transactional
    public int importIrussor(String filename, String edition) {
        Path pdf = Paths.get("..", "rate-books", filename);
        if (!Files.exists(pdf)) {
            throw new IllegalArgumentException("Rate-book file not found: " + pdf.toAbsolutePath());
        }
        List<IrussorItem> items;
        try {
            items = irussorImporter.parse(pdf.toFile(), edition);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the PDF: " + e.getMessage(), e);
        }
        irussorItemRepository.deleteByEdition(edition);
        irussorItemRepository.saveAll(items);
        return items.size();
    }

    @Transactional
    public int importDsr(String edition, String... files) {
        String[] volumes = (files != null && files.length > 0)
                ? files
                : new String[]{"dsr-2021-vol1.pdf", "dsr-2021-vol2.pdf"};
        List<DsrItem> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String f : volumes) {
            Path pdf = Paths.get("..", "rate-books", f);
            if (!Files.exists(pdf)) continue;
            List<DsrItem> parsed;
            try {
                parsed = dsrImporter.parse(pdf.toFile(), edition);
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read " + f + ": " + e.getMessage(), e);
            }
            for (DsrItem it : parsed) {
                if (seen.add(it.getItemCode())) {
                    all.add(it);
                }
            }
        }
        if (all.isEmpty()) {
            throw new IllegalArgumentException("No DSR items parsed - are the volume PDFs in rate-books/?");
        }
        dsrItemRepository.deleteByEdition(edition);
        dsrItemRepository.saveAll(all);
        return all.size();
    }
}
