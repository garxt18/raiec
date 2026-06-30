package com.raiec.tender.repository;

import com.raiec.tender.entity.BreakupItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BreakupItemRepository extends JpaRepository<BreakupItem, Long> {

    List<BreakupItem> findByScheduleEntryId(Long scheduleEntryId);
}
