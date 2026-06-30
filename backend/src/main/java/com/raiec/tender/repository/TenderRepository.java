package com.raiec.tender.repository;

import com.raiec.tender.entity.Tender;
import com.raiec.tender.entity.TenderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenderRepository extends JpaRepository<Tender, Long> {

    Optional<Tender> findByTenderNo(String tenderNo);

    boolean existsByTenderNo(String tenderNo);

    List<Tender> findByStatus(TenderStatus status);
}
