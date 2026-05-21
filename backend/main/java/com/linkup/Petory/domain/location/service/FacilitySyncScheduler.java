package com.linkup.Petory.domain.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacilitySyncScheduler {

    private final FacilitySyncService facilitySyncService;

    @Scheduled(cron = "0 0 1 * * *")
    public void scheduledSync() {
        log.info("[FacilitySyncScheduler] 자동 동기화 시작");
        try {
            FacilitySyncService.SyncResult result = facilitySyncService.syncFromPetDataApi();
            log.info("[FacilitySyncScheduler] 완료 total={} saved={} duplicate={} skipped={}",
                    result.getTotal(), result.getSaved(), result.getDuplicate(), result.getSkipped());
        } catch (Exception e) {
            log.error("[FacilitySyncScheduler] 실패: {}", e.getMessage(), e);
        }
    }
}
