package com.linkup.Petory.domain.admin.service;

import com.linkup.Petory.domain.admin.entity.AdminAuditLog;
import com.linkup.Petory.domain.admin.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long adminIdx, String action, String targetType, Long targetIdx, String detail) {
        try {
            auditLogRepository.save(AdminAuditLog.builder()
                    .adminIdx(adminIdx)
                    .action(action)
                    .targetType(targetType)
                    .targetIdx(targetIdx)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            log.error("감사 로그 저장 실패: adminIdx={}, action={}, error={}", adminIdx, action, e.getMessage());
        }
    }
}
