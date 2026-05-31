package com.linkup.Petory.global.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class PetIntentAsyncConfig {

    /**
     * NLP 분석 전용 bounded executor.
     *
     * 동작 순서: core(2) → queue(500) → max(6) → reject
     * - 평시: core=2 스레드만 동작
     * - 버스트: queue 500까지 적재 (500ms/task 기준 약 125초 분량의 backlog 흡수)
     * - queue 포화 후에야 max=6까지 스레드 증가
     * - 그 이후 유입은 DiscardWithWarnPolicy로 폐기
     *
     * reject 시 일부 signal 생성이 생략된다.
     * 추천 signal은 부가 기능이므로 이는 의도된 trade-off다 (핵심 요청에 영향 없음).
     */
    @Bean("petIntentExecutor")
    public Executor petIntentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("pet-intent-");
        executor.setRejectedExecutionHandler(new DiscardWithWarnPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Java 기본 DiscardPolicy는 로그를 남기지 않는다.
     * 직접 구현해 warn 로그 후 폐기한다.
     * static inner class이므로 outer @Slf4j log 필드 접근 불가 → 별도 Logger 선언.
     */
    static class DiscardWithWarnPolicy implements RejectedExecutionHandler {
        private static final Logger log = LoggerFactory.getLogger(DiscardWithWarnPolicy.class);

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            log.warn("[petIntentExecutor] NLP 분석 작업 폐기 — 큐 포화. active={} queued={}",
                    executor.getActiveCount(), executor.getQueue().size());
        }
    }
}
