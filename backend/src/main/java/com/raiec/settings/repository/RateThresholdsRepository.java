package com.raiec.settings.repository;

import com.raiec.settings.entity.RateThresholds;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateThresholdsRepository extends JpaRepository<RateThresholds, Long> {
}
