package com.raiec.lar.repository;

import com.raiec.lar.entity.LarRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LarRecordRepository extends JpaRepository<LarRecord, Long> {

    Optional<LarRecord> findByLarCode(String larCode);

    /** Count existing records for a year prefix (e.g. "LAR-2026-") to generate the next sequential code. */
    long countByLarCodeStartingWith(String prefix);
}
