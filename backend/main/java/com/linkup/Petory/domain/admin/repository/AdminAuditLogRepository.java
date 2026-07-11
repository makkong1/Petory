package com.linkup.Petory.domain.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.admin.entity.AdminAuditLog;

/**
 * AdminAuditLog Spring Data JPA 저장소.
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
