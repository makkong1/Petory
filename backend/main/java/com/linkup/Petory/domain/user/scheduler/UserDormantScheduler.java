package com.linkup.Petory.domain.user.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.service.UserDormantService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDormantScheduler {

    private final UserDormantService userDormantService;

    /**
     * 매일 자정에 1년 미로그인 사용자를 휴면 전환
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 자정
    public void markDormantUsers() {
        log.info("휴면 계정 전환 배치 시작");
        try {
            userDormantService.markDormantUsers();
            log.info("휴면 계정 전환 배치 완료");
        } catch (Exception e) {
            log.error("휴면 계정 전환 배치 실패", e);
        }
    }
}
