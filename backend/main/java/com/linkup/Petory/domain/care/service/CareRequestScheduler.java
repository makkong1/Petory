package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 펫케어 요청의 상태를 자동으로 업데이트하는 스케줄러
 * 날짜가 지난 요청은 자동으로 COMPLETED 상태로 변경
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareRequestScheduler {

    private final CareRequestRepository careRequestRepository;

    /**
     * 매 시간마다 실행 (정각에 실행)
     * 날짜가 지난 OPEN 또는 IN_PROGRESS 상태의 요청을 COMPLETED로 변경
     */
    @Scheduled(cron = "0 0 * * * ?") // 매 시간 정각에 실행
    @Transactional
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

        int updatedCount = 0;
        for (CareRequest request : expiredRequests) {
            request.setStatus(CareRequestStatus.COMPLETED);
            updatedCount++;
            log.debug("펫케어 요청 상태 변경: id={}, title={}, date={}, status=OPEN/IN_PROGRESS -> COMPLETED",
                    request.getIdx(), request.getTitle(), request.getDate());
        }

        careRequestRepository.saveAll(expiredRequests);
        log.info("펫케어 요청 상태 자동 업데이트 완료: {}건의 요청이 COMPLETED로 변경됨", updatedCount);
    }

    /**
     * 매일 자정에 실행 (더 정확한 시간 체크)
     * 위의 매시간 실행과 중복되지만, 더 정확한 처리를 위해 유지
     */
    @Scheduled(cron = "0 0 0 * * ?") // 매일 자정에 실행
    @Transactional
    public void updateExpiredCareRequestsDaily() {
        log.info("펫케어 요청 일일 상태 업데이트 시작");
        updateExpiredCareRequests();
    }
}
