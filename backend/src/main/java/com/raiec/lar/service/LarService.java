package com.raiec.lar.service;

import com.raiec.lar.repository.LarRecordRepository;
import com.raiec.lar.web.dto.LarRecordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LarService {

    private final LarRecordRepository larRecordRepository;

    public LarService(LarRecordRepository larRecordRepository) {
        this.larRecordRepository = larRecordRepository;
    }

    @Transactional(readOnly = true)
    public List<LarRecordResponse> listAll() {
        return larRecordRepository.findAll().stream()
                .map(LarRecordResponse::from)
                .toList();
    }
}
