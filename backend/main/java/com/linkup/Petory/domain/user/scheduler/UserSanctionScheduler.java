package com.linkup.Petory.domain.user.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.service.UserSanctionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSanctionScheduler {

    private final UserSanctionService userSanctionService;

    /**
     * 매일 자정에 만료된 이용제한 자동 해제
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void releaseExpiredSuspensions() {
        log.info("만료된 이용제한 자동 해제 작업 시작");
        try {
            userSanctionService.releaseExpiredSuspensions();
            log.info("만료된 이용제한 자동 해제 작업 완료");
        } catch (Exception e) {
            log.error("만료된 이용제한 자동 해제 작업 실패", e);
        }
    }
}

