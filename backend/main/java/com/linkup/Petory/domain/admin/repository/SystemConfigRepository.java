package com.linkup.Petory.domain.admin.repository;

import com.linkup.Petory.domain.admin.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** SystemConfig Spring Data JPA 저장소. */
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {
    Optional<SystemConfig> findByConfigKey(String configKey);
}
