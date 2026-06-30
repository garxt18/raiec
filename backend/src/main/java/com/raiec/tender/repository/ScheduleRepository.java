package com.raiec.tender.repository;

import com.raiec.tender.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByTenderId(Long tenderId);
}
