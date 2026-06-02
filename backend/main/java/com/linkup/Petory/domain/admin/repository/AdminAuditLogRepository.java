package com.linkup.Petory.domain.admin.repository;

import com.linkup.Petory.domain.admin.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** AdminAuditLog Spring Data JPA 저장소. */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
