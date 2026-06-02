package com.linkup.Petory.domain.admin.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.admin.entity.AdminAuditLog;
import com.linkup.Petory.domain.admin.repository.AdminAuditLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
/** 관리자 행위를 비동기로 감사 로그에 저장하는 서비스. 로그 저장 실패가 원본 트랜잭션에 영향을 주지 않는다. */
public class AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;

    /** 관리자 행위를 비동기·별도 트랜잭션으로 기록한다. 저장 실패 시 warn 로그만 남기고 예외를 삼킨다. */
    @SuppressWarnings("null")
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
