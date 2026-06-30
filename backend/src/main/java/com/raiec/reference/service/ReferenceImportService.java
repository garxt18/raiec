package com.raiec.reference.service;

import com.raiec.reference.entity.IrussorItem;
import com.raiec.reference.entity.DsrItem;
import com.raiec.reference.ingest.IrussorImporter;
import com.raiec.reference.ingest.DsrImporter;
import com.raiec.reference.repository.IrussorItemRepository;
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

    public ReferenceImportService(IrussorImporter irussorImporter,
                                  IrussorItemRepository irussorItemRepository,
                                  DsrImporter dsrImporter,
                                  DsrItemRepository dsrItemRepository) {
        this.irussorImporter = irussorImporter;
        this.irussorItemRepository = irussorItemRepository;
        this.dsrImporter = dsrImporter;
        this.dsrItemRepository = dsrItemRepository;
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
