package com.linkup.Petory.domain.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.location.entity.LocationSyncLog;

public interface LocationSyncLogRepository extends JpaRepository<LocationSyncLog, Long> {
}
