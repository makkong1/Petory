package com.linkup.Petory.domain.location.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터 시설 동기화 스케줄러. 매일 새벽 3시에 전체 upsert 를 실행한다.
 * 스케줄링 on/off 는 SchedulingConfig(petory.scheduling.enabled)가 중앙 제어.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataSyncScheduler {

    private final PublicDataSyncService publicDataSyncService;

    /** 매일 18:10 실행. 스케줄러 스레드가 1개뿐이라 자정(4개)·정각(2개)·17:10·18:30 슬롯을 피한다. */
    @Scheduled(cron = "0 10 18 * * *")
    public void runDailySync() {
        try {
            LocationSyncLog result = publicDataSyncService.syncFromApi(SyncTriggerType.SCHEDULED);
            log.info("공공데이터 정기 동기화 종료: status={}, 신규={}, 갱신={}, 스킵={}, 실패={}",
                    result.getStatus(), result.getInserted(), result.getUpdated(),
                    result.getSkipped(), result.getFailed());
        } catch (Exception e) {
            // syncFromApi 내부에서 대부분 처리되지만, 최후 방어로 스케줄러 스레드가 죽지 않게 감싼다.
            log.error("공공데이터 정기 동기화 중 예외: {}", e.getMessage(), e);
        }
    }
}
