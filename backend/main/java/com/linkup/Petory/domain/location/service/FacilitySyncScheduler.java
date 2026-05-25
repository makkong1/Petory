package com.linkup.Petory.domain.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacilitySyncScheduler {

    private final LocationImportService locationImportService;

    @Value("${app.location.import.file-path:}")
    private String importFilePath;

    @Scheduled(cron = "0 0 1 * * *")
    public void scheduledSync() {
        if (!StringUtils.hasText(importFilePath)) {
            log.warn("[FacilitySyncScheduler] app.location.import.file-path 미설정 — sync 스킵");
            return;
        }
        log.info("[FacilitySyncScheduler] 파일 기반 import 시작: {}", importFilePath);
        try {
            LocationImportService.SyncResult result = locationImportService.importFromFile(importFilePath);
            log.info("[FacilitySyncScheduler] 완료 total={} saved={} duplicate={} skipped={}",
                    result.getTotal(), result.getSaved(), result.getDuplicate(), result.getSkipped());
        } catch (Exception e) {
            log.error("[FacilitySyncScheduler] 실패: {}", e.getMessage(), e);
        }
    }
}
