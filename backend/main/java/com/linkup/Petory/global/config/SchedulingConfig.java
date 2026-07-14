package com.linkup.Petory.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 설정. 기본값은 활성이며, 쿼리 감사처럼 스케줄러가 측정을 오염시키는 상황에서만
 * {@code --petory.scheduling.enabled=false} 로 끈다.
 *
 * 끄는 이유: MeetupChatRoomRecoveryScheduler 가 5분마다 돌면서 performance_schema digest 에
 * 자기 쿼리를 섞어 넣어, 감사 중인 도메인의 쿼리와 구분되지 않는다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "petory.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
