package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 펫케어 요청의 상태를 자동으로 업데이트하는 스케줄러
 * 날짜가 지난 요청은 자동으로 COMPLETED 상태로 변경
 * 
 * 변경 이력:
 * - 2026-01-28: CareRequestService.updateStatus()를 호출하여 에스크로 처리 포함
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareRequestScheduler {

    private final CareRequestRepository careRequestRepository;
    private final CareRequestService careRequestService;

    /**
     * 매 시간마다 실행 (정각에 실행)
     * 날짜가 지난 OPEN 또는 IN_PROGRESS 상태의 요청을 COMPLETED로 변경
     * 
     * 변경 사항:
     * - 직접 상태 변경 대신 CareRequestService.updateStatus() 호출
     * - 에스크로 처리 로직이 포함된 서비스 메서드 사용
     * - 개별 요청별 예외 처리 추가
     * 스케줄러 메서드에 @Transactional을 두지 않음 — 루프 전체를 한 TX로 묶으면
     * updateStatus(에스크로 등)와 합쳐져 {@code UnexpectedRollbackException}이 날 수 있음.
     */
    @Scheduled(cron = "0 0 * * * ?") // 매 시간 정각에 실행
    public void updateExpiredCareRequests() {
        log.info("펫케어 요청 상태 자동 업데이트 시작");

        LocalDateTime now = LocalDateTime.now();

        // 날짜가 지났고, OPEN 또는 IN_PROGRESS 상태인 요청 조회
        List<CareRequest> expiredRequests = careRequestRepository
                .findByDateBeforeAndStatusIn(
                        now,
                        List.of(CareRequestStatus.OPEN, CareRequestStatus.IN_PROGRESS));

        if (expiredRequests.isEmpty()) {
            log.info("만료된 펫케어 요청이 없습니다.");
            return;
        }

        int totalCount = expiredRequests.size();
        int successCount = 0;
        int failureCount = 0;

        for (CareRequest request : expiredRequests) {
            try {
                // 제재된 당사자가 있는 케어는 자동 완료하지 않음 (해제/관리자 검토 대상)
                if (hasSanctionedParty(request)) {
                    log.warn("자동 완료 스킵 (케어 당사자 제재 중): careId={}, requesterId={}",
                            request.getIdx(), request.getUser().getIdx());
                    continue;
                }

                // 서비스 메서드를 통해 상태 변경 (에스크로 처리 포함)
                // 스케줄러는 시스템 작업이므로 currentUserId는 null
                careRequestService.updateStatus(
                        request.getIdx(),
                        "COMPLETED",
                        null);
                successCount++;
                log.debug("펫케어 요청 상태 변경 완료: id={}, title={}, date={}, status=OPEN/IN_PROGRESS -> COMPLETED",
                        request.getIdx(), request.getTitle(), request.getDate());
            } catch (Exception e) {
                failureCount++;
                log.error("펫케어 요청 상태 변경 실패: id={}, title={}, date={}, error={}",
                        request.getIdx(), request.getTitle(), request.getDate(), e.getMessage(), e);
            }
        }

        log.info("펫케어 요청 상태 자동 업데이트 완료: 총 {}건 중 성공 {}건, 실패 {}건",
                totalCount, successCount, failureCount);
    }

    private boolean hasSanctionedParty(CareRequest request) {
        if (request.getUser().isSanctioned()) {
            return true;
        }
        if (request.getApplications() == null) {
            return false;
        }
        return request.getApplications().stream()
                .filter(app -> app.getStatus() == CareApplicationStatus.ACCEPTED)
                .anyMatch(app -> app.getProvider().isSanctioned());
    }

    /**
     * 매일 자정에 실행 (더 정확한 시간 체크)
     * 위의 매시간 실행과 중복되지만, 더 정확한 처리를 위해 유지
     */
    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정에 실행
    public void updateExpiredCareRequestsDaily() {
        log.info("펫케어 요청 일일 상태 업데이트 시작");
        updateExpiredCareRequests();
    }
}
