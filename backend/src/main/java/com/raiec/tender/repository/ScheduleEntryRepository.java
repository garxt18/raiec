package com.raiec.tender.repository;

import com.raiec.tender.entity.ScheduleEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {

    List<ScheduleEntry> findByScheduleId(Long scheduleId);
}
