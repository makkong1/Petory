package com.linkup.Petory.domain.petRecommendation.repository;

import com.linkup.Petory.domain.petRecommendation.entity.SignalInteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignalInteractionLogRepository extends JpaRepository<SignalInteractionLog, Long> {
}
