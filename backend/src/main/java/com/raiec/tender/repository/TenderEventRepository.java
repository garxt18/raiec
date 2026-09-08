package com.raiec.tender.repository;

import com.raiec.tender.entity.TenderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TenderEventRepository extends JpaRepository<TenderEvent, Long> {

    /** Oldest first: the trail is read as a story, and stories start at the beginning. */
    List<TenderEvent> findByTenderIdOrderByAtAsc(Long tenderId);

    /** Newest first, for the dashboard's activity feed. */
    List<TenderEvent> findTop20ByOrderByAtDesc();

    List<TenderEvent> findByTypeOrderByAtDesc(String type);

    void deleteByTenderId(Long tenderId);
}
